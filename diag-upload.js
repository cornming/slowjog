// 測試頁共用：使用者自己打開「回傳測試紀錄」開關時，才把頁面上的測試紀錄送到收集端的「診斷紀錄」分頁，
// 這樣開發者可以直接看到結果，使用者不用複製貼上。預設關閉，重新整理頁面就回到關閉。
// 只送：頁面上看得到的紀錄行、瀏覽器的 userAgent、一個隨機的測試代號。沒有帳號、沒有裝置編號、不存 IP。
(function () {
  // 和 App 的統計共用同一個收集端（見 web/src/web.rs 的 STATS_URL）
  const ENDPOINT = 'https://script.google.com/macros/s/AKfycbzN1cM7oU-eeatBeISxnm3n8CbYiQej-aOfmIGljFUAAFmRtcP-9NruWs8_RMkhsSpkVA/exec';
  const FLUSH_MS = 4000;        // 前景時每幾毫秒送一次
  const MAX_BATCH_CHARS = 3500; // 收集端每批最多 4000 字
  const run = Array.from(crypto.getRandomValues(new Uint8Array(4)), (b) => b.toString(16).padStart(2, '0')).join('');

  let on = false, page = '', queue = [], sentLines = 0, timer = null, statusEl = null, seq = 0;

  function showStatus() {
    if (!statusEl) return;
    statusEl.textContent = on
      ? `回傳中。測試代號 ${run}，已送出 ${sentLines} 行${queue.length ? `、等待送出 ${queue.length} 行` : ''}（對方有沒有收到，要由開發者確認）。`
      : `沒有回傳。測試代號 ${run}。`;
  }

  function send(body, beacon) {
    try {
      // sendBeacon 在頁面要被隱藏或關閉時最可靠；沒有或被拒絕就改用 fetch。
      // 文字用 text/plain：這樣是「簡單請求」，不需要預檢，收集端（Apps Script）才收得到。
      if (beacon && navigator.sendBeacon && navigator.sendBeacon(ENDPOINT, new Blob([body], { type: 'text/plain;charset=UTF-8' }))) return;
      fetch(ENDPOINT, { method: 'POST', mode: 'no-cors', keepalive: true, body }).catch(() => {});
    } catch (e) { /* 回傳只是附加功能，失敗就放棄，不能影響測試 */ }
  }

  function flush(beacon) {
    if (!on) return;
    while (queue.length) {
      let text = '', n = 0;
      while (n < queue.length && (text + queue[n] + '\n').length <= MAX_BATCH_CHARS) { text += queue[n] + '\n'; n++; }
      if (n === 0) { text = queue[0].slice(0, MAX_BATCH_CHARS); n = 1; } // 單行超長就截斷
      queue.splice(0, n);
      sentLines += n;
      // 流水號：收集端同時處理多個請求，試算表的列順序不等於時間順序；有了流水號可以排序，也看得出缺了哪一批
      send(JSON.stringify({ type: 'diag', run, page, text: '#' + (++seq) + '\n' + text }), beacon);
    }
    showStatus();
  }

  function setOn(value, backlog) {
    on = value;
    clearInterval(timer);
    if (on) {
      queue.push('（開始回傳）userAgent：' + navigator.userAgent);
      if (backlog) backlog().split('\n').filter(Boolean).forEach((l) => queue.push(l));
      timer = setInterval(() => flush(false), FLUSH_MS);
      flush(false);
    } else {
      queue = [];
    }
    showStatus();
  }

  // 頁面要被藏起來（鎖屏、切到別的 App）之前盡量送出；回到前景時馬上補送
  document.addEventListener('visibilitychange', () => { if (on) flush(document.visibilityState === 'hidden'); });
  addEventListener('pagehide', () => flush(true));

  window.DiagUpload = {
    /** 紀錄一行（頁面的 log 函式每寫一行就呼叫一次）。開關沒打開時什麼都不做。 */
    push(line) { if (on) queue.push(line); },
    /** 在 el 裡畫出開關與說明。backlog() 回傳目前頁面上已有的紀錄，打開開關時一併送出。 */
    mount(el, pageName, backlog) {
      page = pageName;
      el.innerHTML =
        '<label class="diag-row"><input type="checkbox" id="diagSwitch"> <b>把測試紀錄自動回傳給開發者</b>（測試時才打開）</label>' +
        '<p class="note">打開後，這個頁面上的測試紀錄，加上你瀏覽器的型號與版本，會自動送給開發者，你就不用再複製貼上。' +
        '不會送帳號、位置、IP 或其他資料。測試完請關掉；重新整理頁面會自動回到關閉。</p>' +
        '<p class="note" id="diagStatus"></p>';
      statusEl = el.querySelector('#diagStatus');
      el.querySelector('#diagSwitch').addEventListener('change', (e) => setOn(e.target.checked, backlog));
      showStatus();
    },
  };
})();
