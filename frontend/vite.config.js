import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The backend listens on 8081 by default (SERVER_PORT). /api is proxied so no CORS setup is needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: { '/api': process.env.VITE_API_TARGET || 'http://localhost:8081' },
  },
});
