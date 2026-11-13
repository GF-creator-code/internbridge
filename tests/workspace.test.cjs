const { test } = require('node:test');
const assert = require('node:assert/strict');
const { parseBatch, mergeJobs, restoreJobs } = require("../front/public/workspace/records.js");
test('imports both markets and deduplicates repeat links without losing progress', () => {
  const hk = parseBatch(' Analyst intern | Example HK | https://example.org/hk#apply ', 'hk');
  const cn = parseBatch('研究實習 | 示例 | https://example.org/cn', 'cn');
  const original = { ...hk[0], status: 'interview', resume: 'English CV' };
  const merged = mergeJobs([original], [...hk, ...cn]);
  assert.deepEqual(merged.map(j => j.market), ['hk', 'cn']);
  assert.equal(merged[0].status, 'interview');
  assert.equal(merged[0].resume, 'English CV');
});
test('rejects malformed batches atomically, dangerous URLs and unknown backup versions', () => {
  assert.throws(() => parseBatch('https://example.org/job\nWrong | https://example.org/invalid', 'hk'));
  for (const url of ['javascript:alert(1)', 'data:text/html,test', 'https://u:p@example.org']) {
    assert.throws(() => parseBatch(url, 'cn'));
  }
  assert.throws(() => restoreJobs({version: 99, jobs: []}));
});
test('restores a full backup and ignores duplicates without losing job notes or the deadline', () => {
  const [job] = parseBatch('https://example.org/job', 'cn');
  Object.assign(job, { notes: 'Reply Friday', due: '2026-12-01', resume: '中文 CV', status: 'applied' });
  assert.deepEqual(restoreJobs({version: 1, jobs: [job, job]}), [job]);
  assert.throws(() => restoreJobs({version: 1, jobs: [{...job, market: 'unknown'}]}));
});
