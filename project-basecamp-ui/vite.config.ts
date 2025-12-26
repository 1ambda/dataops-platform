import path from 'path'
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react-swc'
import tailwindcss from '@tailwindcss/vite'
import { tanstackRouter } from '@tanstack/router-plugin/vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    tanstackRouter({
      target: 'react',
      autoCodeSplitting: true,
    }),
    react(),
    tailwindcss(),
  ],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8070',
        changeOrigin: true,
        secure: false,
        ws: true,
        configure: (proxy) => {
          proxy.on('error', (err) => {
            // eslint-disable-next-line no-console
            console.error('proxy error', err)
          })

          proxy.on('proxyReq', (_proxyReq, req) => {
            // eslint-disable-next-line no-console
            console.error('Sending Request to the Target:', req.method, req.url)
          })
          proxy.on('proxyRes', (_proxyRes, req) => {
            // eslint-disable-next-line no-console
            console.error(
              'Received Response from the Target:',
              _proxyRes.statusCode,
              req.url
            )
          })
        },
      },
    },
  },
})
