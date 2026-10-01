import { useState } from 'react';
import {
  Box, Button, Card, CardContent, Chip, CircularProgress, Collapse, FormControl, IconButton, Link, MenuItem,
  Select, Stack, Tooltip, Typography,
} from '@mui/material';
import ContentCopyIcon from '@mui/icons-material/ContentCopy';
import DownloadIcon from '@mui/icons-material/Download';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import { copyText } from './copy.js';
import { downloadResume } from './api.js';

export const STATUSES = ['NEW', 'APPLIED', 'REFERRED', 'INTERVIEW'];

function scoreColor(score) {
  if (score >= 70) return 'success';
  if (score >= 40) return 'warning';
  return 'default';
}

export default function JobCard({ job, onStatusChange, onNotice }) {
  const [open, setOpen] = useState(false);
  const [building, setBuilding] = useState(false);
  const hasDrafts = Boolean(job.hiringManagerMessage && job.employeeMessage);

  const copy = async (text, label) => onNotice(await copyText(text) ? `${label} copied` : 'Copy failed');

  const download = async () => {
    setBuilding(true);
    try {
      await downloadResume(job.id);
    } catch (e) {
      onNotice(`Could not build resume: ${e.message}`);
    } finally {
      setBuilding(false);
    }
  };

  return (
    <Card variant="outlined" sx={{ mb: 2 }}>
      <CardContent>
        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2} sx={{ justifyContent: 'space-between' }}>
          <Box sx={{ minWidth: 0 }}>
            <Typography variant="h6" component="h2">
              {job.url ? (
                <Link href={job.url} target="_blank" rel="noopener noreferrer" underline="hover" color="inherit">
                  {job.title}
                </Link>
              ) : job.title}
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {[job.employer, job.location].filter(Boolean).join(' · ')}
            </Typography>
          </Box>
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexShrink: 0 }}>
            <Chip label={`Fit ${job.score}`} color={scoreColor(job.score)} />
            <FormControl size="small">
              <Select
                value={job.status}
                onChange={(e) => onStatusChange(job.id, e.target.value)}
                inputProps={{ 'aria-label': 'Status' }}
              >
                {STATUSES.map((s) => (
                  <MenuItem key={s} value={s}>{s[0] + s.slice(1).toLowerCase()}</MenuItem>
                ))}
              </Select>
            </FormControl>
          </Stack>
        </Stack>

        <Typography variant="body2" sx={{ mt: 1.5 }}>{job.reason}</Typography>

        <Stack direction="row" sx={{ mt: 2, gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
          <Tooltip title="Builds a PDF tailored to this job from your profile (takes a few seconds)">
            <span>
              <Button
                size="small" variant="outlined" disabled={building} onClick={download}
                startIcon={building ? <CircularProgress size={16} /> : <DownloadIcon />}
              >
                {building ? 'Building resume...' : 'Download resume'}
              </Button>
            </span>
          </Tooltip>
          <Tooltip title={hasDrafts ? '' : 'Messages are drafted once a job reaches the score threshold'}>
            <span>
              <Button
                size="small" variant="outlined" startIcon={<ContentCopyIcon />} disabled={!hasDrafts}
                onClick={() => copy(job.hiringManagerMessage, 'Hiring manager message')}
              >
                Copy hiring manager message
              </Button>
            </span>
          </Tooltip>
          <Tooltip title={hasDrafts ? '' : 'Messages are drafted once a job reaches the score threshold'}>
            <span>
              <Button
                size="small" variant="outlined" startIcon={<ContentCopyIcon />} disabled={!hasDrafts}
                onClick={() => copy(job.employeeMessage, 'Employee message')}
              >
                Copy employee message
              </Button>
            </span>
          </Tooltip>
          {hasDrafts && (
            <IconButton
              size="small" aria-label="Preview messages" aria-expanded={open}
              onClick={() => setOpen((o) => !o)}
              sx={{ transform: open ? 'rotate(180deg)' : 'none', transition: 'transform .2s' }}
            >
              <ExpandMoreIcon />
            </IconButton>
          )}
        </Stack>

        <Collapse in={open} unmountOnExit>
          <Stack spacing={2} sx={{ mt: 2 }}>
            <Box>
              <Typography variant="caption" color="text.secondary">Hiring manager</Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{job.hiringManagerMessage}</Typography>
            </Box>
            <Box>
              <Typography variant="caption" color="text.secondary">Employee</Typography>
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{job.employeeMessage}</Typography>
            </Box>
          </Stack>
        </Collapse>
      </CardContent>
    </Card>
  );
}
