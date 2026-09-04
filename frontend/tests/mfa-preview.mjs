// Local-only visual smoke-test fixture. Never contacts a real account/backend.
import process from 'node:process';
import { createServer } from 'vite';

process.env.VITE_API_BASE_URL = '/test-api';
const server = await createServer({
    server: { host: '127.0.0.1', port: 4173, strictPort: true },
    plugins: [{
        name: 'local-mfa-preview',
        configureServer(vite) {
            vite.middlewares.use('/test-api', (req, res) => {
                let body = '';
                req.on('data', chunk => { body += chunk; });
                req.on('end', () => {
                    res.setHeader('Content-Type', 'application/json');
                    let data;
                    try { data = JSON.parse(body || '{}'); } catch { data = {}; }
                    if (req.url === '/api/auth/login') {
                        res.end(JSON.stringify({ mfaRequired: true, challengeToken: 'preview-only',
                            expiresAt: new Date(Date.now() + (data.username === 'expired' ? 3000 : 180000)).toISOString() }));
                    } else if (req.url === '/api/auth/mfa/totp') {
                        res.statusCode = data.code === '999999' ? 429 : 401;
                        res.end(JSON.stringify({ message: 'Preview verification rejection' }));
                    } else {
                        res.statusCode = 401;
                        res.end(JSON.stringify({ message: 'Preview has no saved session' }));
                    }
                });
            });
        },
    }],
});
await server.listen();
server.printUrls();
