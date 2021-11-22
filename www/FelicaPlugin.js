var exec = require('cordova/exec');

exports.startNfc = function (arg0, success, error) {
    exec(success, error, 'FelicaPlugin', 'startnfc', [arg0]);
};
exports.stopNfc = function (arg0, success, error) {
    exec(success, error, 'FelicaPlugin', 'stopnfc', [arg0]);
};
exports.Hello = function (arg0, success, error) {
    exec(success, error, 'FelicaPlugin', 'Hello', [arg0]);
};
