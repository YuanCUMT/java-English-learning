/* 外观：浅色 / 深色 / 跟随系统。
   服务端把用户的选择渲染在 <html data-theme="...">，这里负责：
   1) auto 用系统偏好解析成 light/dark（避免白底闪一下）；
   2) 未登录页面（登录/注册）用本地缓存兜底；
   3) 暴露 window.__drTheme.set()，设置页切换时立即生效。 */
(function () {
  'use strict';
  var root = document.documentElement;
  var media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;

  function readStored() {
    try { return localStorage.getItem('dr.theme'); } catch (e) { return null; }
  }
  function writeStored(v) {
    try { localStorage.setItem('dr.theme', v); } catch (e) {}
  }
  function resolve(mode) {
    if (mode === 'light' || mode === 'dark') return mode;
    return media && media.matches ? 'dark' : 'light';
  }
  function paint(mode) {
    root.setAttribute('data-theme', resolve(mode));
    root.setAttribute('data-theme-mode', mode);
  }

  var server = root.getAttribute('data-theme') || 'auto';
  var stored = readStored();
  var mode = server !== 'auto' ? server : (stored || 'auto');
  paint(mode);

  window.__drTheme = {
    get: function () { return mode; },
    set: function (v) {
      mode = v;
      writeStored(v);
      paint(v);
    },
    /** 服务端说是 auto、本地又存过选择时，把本地选择同步回去 */
    syncIfStale: function () {
      return server === 'auto' && stored && stored !== 'auto' ? stored : null;
    }
  };

  if (media && media.addEventListener) {
    media.addEventListener('change', function () {
      if (mode === 'auto') paint('auto');
    });
  }
})();
