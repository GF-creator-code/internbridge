'use strict';

const InternRecords = (() => {
  const markets = ['hk', 'cn'];
  const statuses = ['saved', 'applied', 'interview', 'offer', 'closed'];

  function jobUrl(value) {
    const url = new URL(value);
    if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) throw new Error('url');
    url.hash = '';
    return url.href;
  }

  function parseBatch(text, market) {
    if (!markets.includes(market)) throw new Error('market');
    const result = [];
    for (const [index, raw] of text.split(/\r?\n/).entries()) {
      if (!raw.trim()) continue;
      try {
        const parts = raw.split('|').map(v => v.trim());
        if (![1, 3].includes(parts.length)) throw new Error();
        const url = jobUrl(parts.at(-1));
        result.push({url, title: parts.length === 3 && parts[0] ? parts[0] : new URL(url).hostname,
          company: parts.length === 3 ? parts[1] : '', market, status: 'saved', resume: '', due: '', notes: ''});
      } catch { throw new Error(String(index + 1)); }
    }
    if (!result.length) throw new Error('1');
    return result;
  }

  function mergeJobs(existing, incoming) {
    const unique = new Map(existing.map(job => [job.url, job]));
    for (const job of incoming) if (!unique.has(job.url)) unique.set(job.url, job);
    return [...unique.values()];
  }

  function restoreJobs(backup) {
    if (backup?.version !== 1 || !Array.isArray(backup.jobs)) throw new Error('format');
    const jobs = backup.jobs.map(raw => {
      if (!raw || !markets.includes(raw.market) || !statuses.includes(raw.status)) throw new Error('record');
      const job = {url: jobUrl(raw.url), market: raw.market, status: raw.status};
      for (const key of ['title', 'company', 'resume', 'due', 'notes']) {
        if (typeof raw[key] !== 'string') throw new Error('record');
        job[key] = raw[key];
      }
      if (!job.title.trim() || (job.due && !/^\d{4}-\d{2}-\d{2}$/.test(job.due))) throw new Error('record');
      return job;
    });
    return mergeJobs([], jobs);
  }

  return {parseBatch, mergeJobs, restoreJobs, statuses};
})();
if (typeof module !== 'undefined') module.exports = InternRecords;
