import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { CssBaseline, ThemeProvider, createTheme, useMediaQuery } from '@mui/material';
import { useMemo } from 'react';
import App from './App.jsx';

function Root() {
  const dark = useMediaQuery('(prefers-color-scheme: dark)');
  const theme = useMemo(() => createTheme({ palette: { mode: dark ? 'dark' : 'light' } }), [dark]);
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <App />
    </ThemeProvider>
  );
}

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <Root />
  </StrictMode>,
);
