import fs from 'node:fs';
import path from 'node:path';
import { createHash, createSign } from 'node:crypto';

const packageName = 'branded.m8c95ddccb53d4a7aa246efe1a4c9dd2b.bsmproperties';
const versionCode = Number(process.env.BSM_MARKET_VERSION_CODE);
const versionName = String(process.env.BSM_MARKET_VERSION_NAME || '').trim();
const credentialPath = process.env.BSM_PLAY_SERVICE_ACCOUNT_FILE;
const statePath = process.env.BSM_PLAY_RELEASE_STATE || 'app/build/outputs/market-production/play-release-state.json';
if (!Number.isSafeInteger(versionCode) || versionCode <= 0 || versionCode > 2100000000 || !versionName || versionName.length > 50) {
  throw Error('The existing MARKET package requires an unused version code and a valid release name.');
}
if (!process.argv[2] || !credentialPath) throw Error('The signed MARKET bundle and existing secure Play service-account file are required.');
const bytes = fs.readFileSync(process.argv[2]);
const sha256 = createHash('sha256').update(bytes).digest('hex');
const account = JSON.parse(fs.readFileSync(credentialPath, 'utf8'));
if (account.type !== 'service_account' || typeof account.client_email !== 'string' || typeof account.private_key !== 'string'
  || (account.token_uri && account.token_uri !== 'https://oauth2.googleapis.com/token')) {
  throw Error('The configured Play credential is not the supported existing Google service account.');
}
const encode = value => Buffer.from(JSON.stringify(value)).toString('base64url');
const issued = Math.floor(Date.now() / 1000);
const claims = encode({ alg: 'RS256', typ: 'JWT' }) + '.' + encode({
  iss: account.client_email, scope: 'https://www.googleapis.com/auth/androidpublisher',
  aud: 'https://oauth2.googleapis.com/token', iat: issued, exp: issued + 3600,
});
const signer = createSign('RSA-SHA256');
signer.update(claims);
const assertion = claims + '.' + signer.sign(account.private_key).toString('base64url');
const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
  method: 'POST', headers: { 'content-type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }),
  signal: AbortSignal.timeout(30000), redirect: 'error',
});
if (!tokenResponse.ok) throw Error(`Existing Play authentication failed (HTTP ${tokenResponse.status}).`);
const { access_token } = await tokenResponse.json();
if (!access_token) throw Error('Play authentication returned no access token.');
const base = 'https://androidpublisher.googleapis.com/androidpublisher/v3/applications/' + packageName;
let state = fs.existsSync(statePath) ? JSON.parse(fs.readFileSync(statePath, 'utf8')) : {};
if (state.sha256 && (state.sha256 !== sha256 || state.packageName !== packageName || state.versionCode !== versionCode)) {
  throw Error('The saved operation belongs to a different candidate. Reconcile that operation before starting another release.');
}
const save = phase => {
  state = { ...state, packageName, sha256, versionCode, versionName, track: 'production', phase, updatedAt: new Date().toISOString() };
  fs.mkdirSync(path.dirname(statePath), { recursive: true });
  fs.writeFileSync(statePath, JSON.stringify(state, null, 2));
};
async function api(url, method = 'GET', body) {
  let response;
  try {
    response = await fetch(url, {
      method, headers: { authorization: 'Bearer ' + access_token, ...(body ? { 'content-type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(60000), redirect: 'error',
    });
  } catch {
    throw Error('The Play operation result is unknown. Preserve this run artifact and resume the same edit before repeating a mutation.');
  }
  if (!response.ok) throw Error(`Play ${method} failed (HTTP ${response.status}). Keep the saved edit for reconciliation.`);
  return response.status === 204 ? {} : await response.json();
}
const releaseFor = tracks => (tracks.tracks || []).find(track => track.track === 'production')?.releases
  ?.find(release => (release.versionCodes || []).map(Number).includes(versionCode));
const codesFor = tracks => (tracks.tracks || []).flatMap(track => (track.releases || []).flatMap(release => release.versionCodes || [])).map(Number);
function report(status, releaseStatus) {
  console.log(JSON.stringify({ packageName, versionCode, versionName, status, playReleaseStatus: releaseStatus,
    availability: 'not_verified', storeUrl: 'https://play.google.com/store/apps/details?id=' + packageName }));
}
if (!state.editId) {
  save('opening_edit');
  const edit = await api(base + '/edits', 'POST', {});
  state.editId = edit.id;
  save('edit_open');
}
const editBase = base + '/edits/' + encodeURIComponent(state.editId);
let tracks;
try { tracks = await api(editBase + '/tracks'); }
catch (error) {
  if (!['committing', 'submitted_to_play'].includes(state.phase)) throw error;
  // Committed edits close. Read live state in a fresh edit before claiming the previous commit succeeded.
  const view = await api(base + '/edits', 'POST', {});
  const viewBase = base + '/edits/' + encodeURIComponent(view.id);
  const release = releaseFor(await api(viewBase + '/tracks'));
  const liveBundles = await api(viewBase + '/bundles');
  const matching = (liveBundles.bundles || []).find(bundle => Number(bundle.versionCode) === versionCode && bundle.sha256?.toLowerCase() === sha256);
  await api(viewBase, 'DELETE');
  if (!release || !matching) throw Error('The previous commit remains unresolved. Reconcile this saved Play edit in Console before another release.');
  save('submitted_to_play');
  report('submitted_to_play', release.status);
  process.exit(0);
}
const bundles = await api(editBase + '/bundles');
const existing = (bundles.bundles || []).find(bundle => Number(bundle.versionCode) === versionCode);
if (existing && existing.sha256?.toLowerCase() !== sha256) {
  throw Error('This version code already has a different bundle. Resume its original signed artifact rather than replacing it.');
}
if (codesFor(tracks).some(code => code > versionCode)) throw Error('Google Play contains a newer release. Preserve it and select a newer unused version code.');
const production = releaseFor(tracks);
if (production?.status === 'completed' && existing) {
  await api(editBase, 'DELETE');
  save('submitted_to_play');
  report('already_in_production_track', production.status);
  process.exit(0);
}
if (!existing) {
  if (state.phase === 'uploading_bundle') throw Error('Previous bundle upload is unresolved. Keep the saved edit and reconcile it before another upload.');
  if ([...codesFor(tracks), ...(bundles.bundles || []).map(bundle => Number(bundle.versionCode))].some(code => code >= versionCode)) {
    throw Error('This version code is already used. Select a newer unused code after reconciling the existing release.');
  }
  save('uploading_bundle');
  let upload;
  try {
    upload = await fetch('https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/' + packageName
      + '/edits/' + encodeURIComponent(state.editId) + '/bundles?uploadType=media', {
      method: 'POST', headers: { authorization: 'Bearer ' + access_token, 'content-type': 'application/octet-stream' },
      body: bytes, signal: AbortSignal.timeout(300000), redirect: 'error',
    });
  } catch { throw Error('Bundle upload result is unknown. Resume this run artifact and reconcile its bundle list before another upload.'); }
  if (!upload.ok) throw Error(`Play bundle upload failed (HTTP ${upload.status}). Preserve the saved edit before retrying.`);
  const bundle = await upload.json();
  if (Number(bundle.versionCode) !== versionCode || bundle.sha256?.toLowerCase() !== sha256) throw Error('Google Play bundle identity differs from the signed MARKET candidate.');
  save('bundle_uploaded');
}
if ((tracks.tracks || []).find(track => track.track === 'production')?.releases?.some(release =>
  ['inProgress', 'halted'].includes(release.status) && !(release.versionCodes || []).map(Number).includes(versionCode))) {
  throw Error('Another production rollout is active. Preserve that release and resolve its existing rollout in Play Console.');
}
save('updating_production_track');
await api(editBase + '/tracks/production', 'PUT', {
  track: 'production', releases: [{ name: 'BSM MARKET ' + versionName, versionCodes: [String(versionCode)], status: 'completed' }],
});
save('committing');
await api(editBase + ':commit?changesNotSentForReview=false', 'POST', {});
save('submitted_to_play');
report('submitted_to_play', 'completed');
