// JS 端封装：把原生事件流转成回调。window.TapAdBridge 由 Cordova 注入。
var exec = require('cordova/exec');
function call(action, args, ok, err) { exec(ok || function () {}, err || function () {}, 'TapAd', action, args || []); }
module.exports = {
  init: function (mediaId, mediaName, mediaKey, debug, ok, err) { call('init', [mediaId, mediaName, mediaKey, !!debug], ok, err); },
  isReady: function (kind, cb) { call('isReady', [kind], function (v) { cb(v === 1); }, function () { cb(false); }); },
  preload: function (kind, spaceId, userId, rewardName, rewardAmount, ok, err) { call('preload', [kind, spaceId, userId || '', rewardName || '', String(rewardAmount || 1)], ok, err); },
  show: function (kind, onEvent) { exec(function (o) { onEvent(o); }, function (e) { onEvent({ e: 'error', msg: String(e) }); }, 'TapAd', 'show', [kind]); },
  splash: function (spaceId, timeoutMs, onEvent) { exec(function (o) { onEvent(o); }, function () { onEvent({ e: 'done' }); }, 'TapAd', 'splash', [spaceId, timeoutMs || 3000]); },
  destroy: function () { call('destroy'); }
};
