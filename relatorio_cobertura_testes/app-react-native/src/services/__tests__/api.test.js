import api from '../api';

describe('services/api', () => {
  it('usa timeout de 5 segundos', () => {
    expect(api.defaults.timeout).toBe(5000);
  });

  it('aponta para a API da Raspberry Pi na porta 8080 (/api)', () => {
    expect(api.defaults.baseURL).toMatch(/:8080\/api$/);
  });

  // Controles de segurança ainda NÃO implementados (OWASP Top 10:2025 - A04/A07/A09).
  // Remova o ".failing" / ".todo" quando forem implementados.
  it.failing('A04 - deve usar HTTPS (TLS) na comunicação com o backend', () => {
    expect(api.defaults.baseURL).toMatch(/^https:\/\//);
  });

  it.failing('A07 - deve anexar o token de autenticação nas requisições', async () => {
    const handlers = api.interceptors.request.handlers.filter(Boolean);
    expect(handlers.length).toBeGreaterThan(0);
  });

  it.todo('A09 - deve registrar (sem dados sensíveis) falhas de rede/HTTP');
});
