// Mede a API de embedding do Gemini ISOLADA da aplicação (mesma ideia do 06 para o Ollama).
// Chama gemini-embedding-001 com outputDimensionality=768 (a mesma dimensão do semantic_cache / vector(768)).
//
// A CHAVE NÃO FICA NO ARQUIVO: passe pela variável de ambiente do PowerShell.
//   $env:GEMINI_API_KEY = "cole_sua_chave_aqui"     # só nesta janela do PowerShell
//
// COMECE DEVAGAR: o plano gratuito tem limite diário (RPD) e por minuto (RPM); um teste agressivo
// pode esgotar a cota do dia e derrubar o chat/RAG do seu app. Confira seus limites reais em
// https://aistudio.google.com/rate-limit antes de aumentar.
//
//   1) Latência de uma chamada só (sem fila):
//        k6 run -e GEMINI_API_KEY=$env:GEMINI_API_KEY -e VUS=1 -e SLEEP=1 -e DURATION=15s 07_k6_gemini_embed.js
//   2) Carga moderada (10 VUs, 30s):
//        k6 run -e GEMINI_API_KEY=$env:GEMINI_API_KEY -e VUS=10 -e SLEEP=0 -e DURATION=30s 07_k6_gemini_embed.js
//   3) Só depois, 100 VUs (pode estourar a cota):
//        k6 run -e GEMINI_API_KEY=$env:GEMINI_API_KEY -e VUS=100 -e SLEEP=0 -e DURATION=30s 07_k6_gemini_embed.js
//
// Como ler: compare http_reqs/s e a mediana com o Ollama (~10 req/s, mediana ~11s a 100 VUs).
// Respostas 429 aparecem em "gemini_429": é o limite do plano, não do seu código.
import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const KEY      = __ENV.GEMINI_API_KEY;
const MODEL    = __ENV.MODEL    || 'gemini-embedding-001';
const DIM      = Number(__ENV.DIM || 768);
const VUS      = Number(__ENV.VUS || 1);
const DURATION = __ENV.DURATION || '30s';
const SLEEP    = Number(__ENV.SLEEP ?? 0);

const rate429 = new Counter('gemini_429');
const other   = new Counter('gemini_outros_erros');

export const options = {
    scenarios: {
        embed: { executor: 'constant-vus', vus: VUS, duration: DURATION, gracefulStop: '30s' },
    },
    summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

export function setup() {
    if (!KEY) throw new Error('Defina GEMINI_API_KEY (use -e GEMINI_API_KEY=$env:GEMINI_API_KEY)');
}

export default function () {
    const res = http.post(
        `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:embedContent`,
        JSON.stringify({
            model: `models/${MODEL}`,
            content: { parts: [{ text: `VU${__VU}-it${__ITER}: O que é habeas corpus?` }] },
            outputDimensionality: DIM,
        }),
        { headers: { 'Content-Type': 'application/json', 'x-goog-api-key': KEY }, timeout: '60s' }
    );
    if (res.status === 429) rate429.add(1);
    else if (res.status !== 200) other.add(1);
    check(res, {
        'status 200': (r) => r.status === 200,
        [`dimensão ${DIM}`]: (r) => {
            try { return r.json('embedding.values').length === DIM; } catch (e) { return false; }
        },
    });
    if (SLEEP > 0) sleep(SLEEP);
}