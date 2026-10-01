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

/** Generates the tailored resume for a job (can take several seconds) and saves it as a PDF. */
export async function downloadResume(id) {
  const res = await fetch(`/api/jobs/${id}/resume.pdf`);
  if (!res.ok) {
    let detail = '';
    try {
      detail = (await res.json()).error || '';
    } catch {
      /* body was not JSON */
    }
    throw new Error(detail || `${res.status} ${res.statusText}`);
  }
  const blob = await res.blob();
  const match = /filename="?([^";]+)"?/.exec(res.headers.get('Content-Disposition') || '');
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = match ? match[1] : 'resume.pdf';
  document.body.appendChild(link);
  link.click();
  link.remove();
  URL.revokeObjectURL(url);
}
