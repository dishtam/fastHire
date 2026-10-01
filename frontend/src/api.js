async function request(path, options) {
  const res = await fetch(path, options);
  if (!res.ok) {
    let detail = '';
    try {
      detail = (await res.json()).error || '';
    } catch {
      /* body was not JSON */
    }
    throw new Error(`${res.status} ${res.statusText}${detail ? `: ${detail}` : ''}`);
  }
  return res.json();
}

export const fetchJobs = (region, limit = 20) =>
  request(`/api/jobs?region=${region}&limit=${limit}`);

export const updateStatus = (id, status) =>
  request(`/api/jobs/${id}/status`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ status }),
  });

export const fetchSources = () => request('/api/sources');
