'use strict';

const $ = id => document.getElementById(id);
const key = 'internbridge.jobs.v1';
const langKey = 'internbridge.lang';
const labels = {
  heading:['實習工作台','Internship workspace'], intro:['搜尋兩地實習，記下申請進度與下一個截止日期。','Find internships in both markets and track applications and deadlines.'],
  hongkong:['香港','Hong Kong'], mainland:['中國內地','Mainland China'], sxs:['實習僧','Shixiseng'],
  list:['申請清單','Applications'], add:['加入職位','Add jobs'], market:['職位地區','Market'],
  batch:['職位連結，每行一個','Job links, one per line'], format:['可只貼網址，或填寫「職位 | 公司 | 網址」。同一連結只會加入一次。','Paste a URL or enter “Title | Company | URL”. Duplicate links are skipped.'],
  save:['儲存','Save'], cancel:['取消','Cancel'], progress:['進度','Status'], allMarkets:['兩地全部','Both markets'], allStatuses:['全部進度','All statuses'],
  saved:['待申請','Saved'], applied:['已申請','Applied'], interview:['面試中','Interviewing'], offer:['已獲聘','Offer'], closed:['已結束','Closed'],
  edit:['編輯職位','Edit job'], title:['職位名稱','Job title'], company:['公司','Company'], due:['申請截止日期','Deadline'], resume:['履歷版本或檔名','CV version or filename'], notes:['跟進筆記','Notes'],
  remove:['移除','Remove'], confirmRemove:['從清單移除這個職位？','Remove this job from your list?'], backup:['匯出備份','Export backup'], restore:['匯入備份','Import backup'], tools:['本機平台工具 ↗','Local platform tools ↗'],
  fromPlatform:['從本機採集記錄加入職位','Add jobs from local platform records'], pull:['匯入最近 50 個職位','Import latest 50 jobs'],
  storage:['清單儲存在目前瀏覽器。轉用裝置或清除瀏覽資料前，請先匯出備份。','Your list is stored in this browser. Export a backup before switching devices or clearing browser data.'],
  empty:['清單還是空的。從上方開啟招聘網站，再用「加入職位」貼上感興趣的職位連結。','Your list is empty. Find a role on a job site above, then paste its link using Add jobs.'],
  noMatch:['這個篩選條件下沒有職位。','No jobs match these filters.'], count:['個職位','jobs'], added:['已加入','Added'], duplicate:['重複連結','duplicates'], invalid:['連結或格式有誤，請檢查第','Invalid format or link on line'],
  loadError:['無法讀取本機備份。請使用另一個瀏覽器設定檔，或先從此瀏覽器備份資料。','Cannot read saved data. Preserve this browser profile and use a different one to start a new list.'],
  storeError:['瀏覽器無法儲存更改，請檢查儲存空間及網站權限。','Could not save. Check browser storage space and site permissions.'],
  badFile:['備份檔案無效，請選擇由 InternBridge 匯出的 JSON。','Invalid backup. Select a JSON export from InternBridge.'],
  failedPull:['讀取失敗，請確認已啟動本機服務。','Could not read records. Check that the local service is running.'],
  updated:['已儲存更改','Changes saved'], lineEnd:['行。','.'], skipped:['缺少有效連結','without a valid link']
};
let lang = 'zh', jobs = [], editing = '', locked = false;
let timer;
const t = id => labels[id][lang === 'en' ? 1 : 0];
function announce(message, persistent = false) {
  clearTimeout(timer);
  $('notice').textContent = message;
  $('notice').hidden = false;
  if (!persistent) timer = setTimeout(() => { $('notice').hidden = true; }, 5500);
}
function readSaved() {
  const raw = localStorage.getItem(key);
  return raw ? InternRecords.restoreJobs(JSON.parse(raw)) : [];
}
function change(update) {
  if (locked) { announce(t('loadError'), true); return false; }
  try {
    const next = update(readSaved());
    localStorage.setItem(key, JSON.stringify({version:1, jobs:next}));
    jobs = next;
    renderList();
    return true;
  } catch { announce(t('storeError'), true); return false; }
}
function option(value, label) {
  const el = document.createElement('option'); el.value = value; el.textContent = label; return el;
}
function node(tag, value, css) {
  const el = document.createElement(tag); el.textContent = value;
  if (css) el.className = css;
  return el;
}
function renderList() {
  const market = $('market-filter').value, status = $('status-filter').value;
  const filtered = jobs.filter(job => (!market || market === job.market) && (!status || status === job.status));
  $('count').textContent = filtered.length + ' ' + t('count');
  const list = $('listing'); list.replaceChildren();
  if (!filtered.length) list.append(node('p', t(jobs.length ? 'noMatch' : 'empty'), 'empty'));
  for (const job of filtered) {
    const row = node('article', '', 'row'), details = node('div','');
    const link = node('a',job.title); link.href = job.url; link.target = '_blank'; link.rel = 'noreferrer';
    const title = node('strong',''); title.append(link); details.append(title);
    details.append(node('p',[job.company, t(job.market === 'hk' ? 'hongkong' : 'mainland'), job.due && t('due') + ' ' + job.due, job.resume].filter(Boolean).join(' · '),'muted'));
    const edit = node('button', t('edit')); edit.type = 'button';
    edit.onclick = () => {
      editing = job.url;
      for (const name of ['title','company','market','status','resume','due','notes']) $('edit-form').elements[name].value = job[name];
      $('editor').showModal();
    };
    row.append(details, node('span',t(job.status),'stage'), edit); list.append(row);
  }
}
function translate() {
  document.documentElement.lang = lang === 'en' ? 'en' : 'zh-HK';
  document.title = 'InternBridge · ' + t('heading');
  $('language').textContent = lang === 'en' ? '繁體中文' : 'English';
  document.querySelectorAll('[data-t]').forEach(el => el.textContent = t(el.dataset.t));
  const selected = $('status-filter').value;
  for (const menu of [$('status-filter'), $('edit-form').elements.status]) {
    menu.replaceChildren();
    if (menu.id === 'status-filter') menu.append(option('',t('allStatuses')));
    InternRecords.statuses.forEach(status => menu.append(option(status,t(status))));
  }
  $('status-filter').value = selected;
  renderList();
}
function addJobs(items, extra = '') {
  let added = 0;
  const success = change(existing => {
    const merged = InternRecords.mergeJobs(existing, items);
    added = merged.length - existing.length; return merged;
  });
  if (success) {
    $('market-filter').value = ''; $('status-filter').value = ''; renderList();
    announce(t('added') + ' ' + added + ' · ' + t('duplicate') + ' ' + (items.length - added) + extra);
  }
  return success;
}
try { lang = localStorage.getItem(langKey) === 'en' ? 'en' : 'zh'; jobs = readSaved(); }
catch { locked = true; announce(t('loadError'), true); }
translate();
$('language').onclick = () => {
  lang = lang === 'en' ? 'zh' : 'en';
  try { localStorage.setItem(langKey,lang); } catch {}
  translate();
};
window.addEventListener('storage', event => {
  if (event.key === key || event.key === null) {
    try { jobs = readSaved(); locked = false; renderList(); } catch { locked = true; announce(t('loadError'), true); }
  }
});
$('open-add').onclick = () => { $('add-form').hidden = false; $('add-form').elements.batch.focus(); };
$('cancel-add').onclick = () => { $('add-form').hidden = true; $('open-add').focus(); };
$('add-form').onsubmit = event => {
  event.preventDefault(); const form = event.currentTarget;
  try {
    if (addJobs(InternRecords.parseBatch(form.elements.batch.value,form.elements.market.value))) {
      form.elements.batch.value = ''; form.hidden = true; $('open-add').focus();
    }
  } catch (error) { announce(t('invalid') + ' ' + error.message + ' ' + t('lineEnd')); }
};
$('market-filter').onchange = $('status-filter').onchange = renderList;
$('edit-form').onsubmit = event => {
  event.preventDefault(); const data = Object.fromEntries(new FormData(event.currentTarget));
  data.title = data.title.trim();
  if (!data.title) { event.currentTarget.elements.title.focus(); return; }
  if (change(existing => existing.map(job => job.url === editing ? {...job,...data} : job))) {
    $('editor').close(); announce(t('updated'));
  }
};
$('cancel-edit').onclick = () => $('editor').close();
$('remove').onclick = () => {
  if (confirm(t('confirmRemove')) && change(existing => existing.filter(job => job.url !== editing))) $('editor').close();
};
$('backup').onclick = () => {
  if (locked) { announce(t('loadError'), true); return; }
  const file = new Blob([JSON.stringify({version:1,jobs},null,2)],{type:'application/json'});
  const a = document.createElement('a'); const url = URL.createObjectURL(file);
  a.href = url; a.download = 'internbridge-' + new Date().toISOString().slice(0,10) + '.json';
  a.click(); setTimeout(() => URL.revokeObjectURL(url),2000);
};
$('restore').onclick = () => $('file').click();
$('file').onchange = async event => {
  const file = event.target.files[0]; if (!file) return;
  try { addJobs(InternRecords.restoreJobs(JSON.parse(await file.text()))); }
  catch { announce(t('badFile')); }
  finally { event.target.value = ''; }
};

// The hosted workspace never contacts localhost. Integrated mode is served by the local app.
if (['localhost','127.0.0.1','[::1]'].includes(location.hostname) && new URLSearchParams(location.search).has('local')) {
  $('local-import').hidden = false; $('tools').href = '/jobsdb';
}
$('pull').onclick = async () => {
  const platform = $('source').value, button = $('pull'); button.disabled = true;
  try {
    const response = await fetch('/api/' + platform + '/list?page=1&size=50');
    if (!response.ok) throw new Error();
    const {items} = await response.json();
    const market = ['jobsdb','offertoday'].includes(platform) ? 'hk' : 'cn', jobs = [];
    for (const raw of items) {
      try {
        const [job] = InternRecords.parseBatch(raw.jobLink || raw.jobUrl || '', market);
        job.title = raw.jobName || raw.jobTitle || job.title; job.company = raw.companyName || '';
        if (raw.deliveryStatus === '已投递') job.status = 'applied';
        jobs.push(job);
      } catch {}
    }
    addJobs(jobs, ' · ' + t('skipped') + ' ' + (items.length - jobs.length));
  } catch { announce(t('failedPull')); } finally { button.disabled = false; }
};
