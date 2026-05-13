var cordovaExec = require('cordova/exec');

/**
 * Launch the native document scanner.
 * @param {Object}   [options]
 * @param {number}   [options.maxPages=3]
 * @param {number}   [options.jpegQuality=70]
 * @param {Function} success Callback receiving Array<string> of base64 JPEG (no data: prefix).
 * @param {Function} error   Callback receiving error string.
 */
exports.scan = function (options, success, error) {
  var opts = options || {};
  cordovaExec(success, error, 'JunctionDocScanner', 'scan', [
    opts.maxPages || 3,
    opts.jpegQuality || 70
  ]);
};

exports.isAvailable = function (success, error) {
  cordovaExec(success, error, 'JunctionDocScanner', 'isAvailable', []);
};
