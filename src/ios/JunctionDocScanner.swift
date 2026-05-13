import UIKit
import VisionKit

@objc(JunctionDocScanner) class JunctionDocScanner: CDVPlugin, VNDocumentCameraViewControllerDelegate {

    private var pendingCallbackId: String?
    private var maxPages: Int = 3
    private var jpegQuality: CGFloat = 0.7

    @objc(isAvailable:)
    func isAvailable(command: CDVInvokedUrlCommand) {
        let available: Bool
        if #available(iOS 13.0, *) {
            available = VNDocumentCameraViewController.isSupported
        } else {
            available = false
        }
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: available)
        self.commandDelegate.send(result, callbackId: command.callbackId)
    }

    @objc(scan:)
    func scan(command: CDVInvokedUrlCommand) {
        guard #available(iOS 13.0, *), VNDocumentCameraViewController.isSupported else {
            let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: "VisionKit unavailable")
            self.commandDelegate.send(result, callbackId: command.callbackId)
            return
        }

        if let pages = command.arguments.first as? Int { self.maxPages = max(1, pages) }
        if command.arguments.count > 1, let q = command.arguments[1] as? Int {
            self.jpegQuality = CGFloat(max(1, min(100, q))) / 100.0
        }

        self.pendingCallbackId = command.callbackId

        DispatchQueue.main.async {
            let scanner = VNDocumentCameraViewController()
            scanner.delegate = self
            scanner.modalPresentationStyle = .fullScreen
            self.viewController.present(scanner, animated: true, completion: nil)
        }
    }

    private func sendError(_ message: String) {
        guard let cbId = pendingCallbackId else { return }
        let result = CDVPluginResult(status: CDVCommandStatus_ERROR, messageAs: message)
        self.commandDelegate.send(result, callbackId: cbId)
        pendingCallbackId = nil
    }

    private func sendOk(_ pages: [String]) {
        guard let cbId = pendingCallbackId else { return }
        let result = CDVPluginResult(status: CDVCommandStatus_OK, messageAs: pages)
        self.commandDelegate.send(result, callbackId: cbId)
        pendingCallbackId = nil
    }

    // MARK: - VNDocumentCameraViewControllerDelegate

    @available(iOS 13.0, *)
    func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
        controller.dismiss(animated: true)
        var pages: [String] = []
        let count = min(scan.pageCount, self.maxPages)
        for i in 0..<count {
            let image = scan.imageOfPage(at: i)
            let resized = JunctionDocScanner.resizedJpeg(image, maxLongSide: 1600, quality: self.jpegQuality)
            if let data = resized {
                pages.append(data.base64EncodedString())
            }
        }
        self.sendOk(pages)
    }

    @available(iOS 13.0, *)
    func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
        controller.dismiss(animated: true)
        self.sendError("cancelled")
    }

    @available(iOS 13.0, *)
    func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
        controller.dismiss(animated: true)
        self.sendError(error.localizedDescription)
    }

    // MARK: - Helpers

    private static func resizedJpeg(_ image: UIImage, maxLongSide: CGFloat, quality: CGFloat) -> Data? {
        let w = image.size.width
        let h = image.size.height
        let longSide = max(w, h)
        let scale = longSide > maxLongSide ? maxLongSide / longSide : 1.0
        let newSize = CGSize(width: w * scale, height: h * scale)

        UIGraphicsBeginImageContextWithOptions(newSize, true, 1.0)
        image.draw(in: CGRect(origin: .zero, size: newSize))
        let out = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return out?.jpegData(compressionQuality: quality)
    }
}
