// Mede o Ollama (embedding) ISOLADO da aplicação, do banco e do LLM.
// Hipótese a testar: os ~5s do cache semântico são o embedding (nomic-embed-text em CPU), não o banco.
//
// Uso (PowerShell, na pasta do arquivo):
//   Saturação (sem think time; mostra o teto de req/s e a fila):
//     k6 run -e VUS=100 -e SLEEP=0 06_k6_ollama_embed.js
//   Perfil parecido com o seu teste (100 usuários, think time ~7s):
//     k6 run -e VUS=100 -e SLEEP=7 06_k6_ollama_embed.js
//   Uma chamada só (latência sem fila):
//     k6 run -e VUS=1 -e SLEEP=1 -e DURATION=20s 06_k6_ollama_embed.js
//
// Como ler: se com VUS=1 a latência é ~100-300ms e com VUS=100 ela sobe para segundos
// com throughput travado em ~N req/s, o Ollama serializa e é o gargalo.
import http from 'k6/http';
import { check, sleep } from 'k6';

const URL      = __ENV.OLLAMA_URL || 'http://localhost:11434';
const MODEL    = __ENV.MODEL      || 'nomic-embed-text';
const VUS      = Number(__ENV.VUS || 100);
const DURATION = __ENV.DURATION   || '60s';
const SLEEP    = Number(__ENV.SLEEP ?? 0);

export const options = {
    scenarios: {
        embed: { executor: 'constant-vus', vus: VUS, duration: DURATION, gracefulStop: '30s' },
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export default function () {
    const res = http.post(
        `${URL}/api/embed`,
        JSON.stringify({ model: MODEL, input: `VU${__VU}-it${__ITER}: O que é habeas corpus?` }),
        { headers: { 'Content-Type': 'application/json' }, timeout: '60s' }
    );
    check(res, { 'status 200': (r) => r.status === 200 });
    if (SLEEP > 0) sleep(SLEEP);
}