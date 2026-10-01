import { useEffect, useState } from 'react';
import { Alert, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material';
import { fetchSources } from './api.js';

const LABELS = {
  OK: 'OK',
  ERROR: 'Failed on last run',
  NO_SCRAPER: 'No scraper for this platform yet',
};

function describe(s) {
  if (!s.enabled) return 'Disabled';
  if (s.kind === 'BOARD_EMAIL_ONLY') return 'Job board: handled via email alerts';
  if (s.kind === 'UNSUPPORTED') return 'Unsupported site';
  return LABELS[s.status] || 'Not scraped yet';
}

export default function SourceHealth() {
  const [sources, setSources] = useState(null);
  const [error, setError] = useState(null);

  useEffect(() => {
    fetchSources().then(setSources).catch((e) => setError(e.message));
  }, []);

  if (error) return <Alert severity="error">{error}</Alert>;
  if (!sources) return null;
  if (sources.length === 0) return <Typography color="text.secondary">No sources imported yet.</Typography>;

  return (
    <Table size="small">
      <TableHead>
        <TableRow>
          <TableCell>Region</TableCell>
          <TableCell>Source</TableCell>
          <TableCell>Platform</TableCell>
          <TableCell>Status</TableCell>
        </TableRow>
      </TableHead>
      <TableBody>
        {sources.map((s) => (
          <TableRow key={s.id}>
            <TableCell>{s.region}</TableCell>
            <TableCell sx={{ maxWidth: 360, overflow: 'hidden', textOverflow: 'ellipsis' }}>{s.label || s.url}</TableCell>
            <TableCell>{s.atsType || s.kind}</TableCell>
            <TableCell>{describe(s)}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  );
}
