import { useCallback, useEffect, useState } from 'react';
import {
  Alert, Box, Button, CircularProgress, Container, Snackbar, Tab, Tabs, Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { fetchJobs, updateStatus } from './api.js';
import JobCard from './JobCard.jsx';
import SourceHealth from './SourceHealth.jsx';

const REGIONS = [
  { code: 'IN', label: 'India' },
  { code: 'UAE', label: 'UAE' },
];

export default function App() {
  const [region, setRegion] = useState('IN');
  const [jobs, setJobs] = useState(null);
  const [error, setError] = useState(null);
  const [toast, setToast] = useState('');

  const load = useCallback(() => {
    setJobs(null);
    setError(null);
    fetchJobs(region).then(setJobs).catch((e) => setError(e.message));
  }, [region]);

  useEffect(load, [load]);

  const changeStatus = async (id, status) => {
    const previous = jobs;
    setJobs((list) => list.map((j) => (j.id === id ? { ...j, status } : j)));
    try {
      await updateStatus(id, status);
    } catch (e) {
      setJobs(previous);
      setToast(`Could not update status: ${e.message}`);
    }
  };

  return (
    <Container maxWidth="md" sx={{ py: 3 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1 }}>
        <Typography variant="h4" component="h1">fastHire</Typography>
        <Button startIcon={<RefreshIcon />} onClick={load}>Refresh</Button>
      </Box>

      <Tabs value={region} onChange={(_, v) => setRegion(v)} sx={{ mb: 2 }}>
        {REGIONS.map((r) => <Tab key={r.code} value={r.code} label={r.label} />)}
      </Tabs>

      {error && <Alert severity="error">Could not load jobs: {error}</Alert>}
      {!error && jobs === null && <Box sx={{ textAlign: 'center', py: 6 }}><CircularProgress /></Box>}
      {jobs && jobs.length === 0 && (
        <Typography color="text.secondary" sx={{ py: 4 }}>
          No scored jobs for this region yet. Run the pipeline (POST /admin/run) to fetch and score some.
        </Typography>
      )}
      {jobs && jobs.map((job) => (
        <JobCard key={job.id} job={job} onStatusChange={changeStatus} onCopied={setToast} />
      ))}

      <Typography variant="h6" component="h2" sx={{ mt: 5, mb: 1 }}>Sources</Typography>
      <SourceHealth />

      <Snackbar open={Boolean(toast)} autoHideDuration={2500} onClose={() => setToast('')} message={toast} />
    </Container>
  );
}
