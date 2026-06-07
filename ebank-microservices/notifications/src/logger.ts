import pino from 'pino';

const SERVICE_NAME = 'notifications-service';
const isProduction  = process.env.NODE_ENV === 'production';
const LOKI_URL      = process.env.LOKI_URL ?? 'http://loki:3100/loki/api/v1/push';

// ─── Transport configuration ────────────────────────────────────────────────
//
//  LOCAL  (NODE_ENV != 'production'):
//    pino-pretty  →  colored, human-readable console output
//
//  DOCKER / PROD (NODE_ENV = 'production'):
//    pino-loki    →  push structured logs to Loki (batched, async)
//    + raw JSON console (stdout) so `docker logs` stays useful
//
const transport = isProduction
  ? pino.transport({
      targets: [
        // Raw JSON to stdout — parseable by Docker/kubectl
        { target: 'pino/file', options: { destination: 1 } },
        // Push to Loki
        {
          target: 'pino-loki',
          options: {
            host:   LOKI_URL,
            // Low-cardinality labels — same convention as Java logback-spring.xml
            labels: { service: SERVICE_NAME, env: 'docker' },
            // Include traceId in the log line (populated by OpenTelemetry in step 3)
            replaceTimestamp: true,
            silenceErrors:    false,
          },
        },
      ],
    })
  : pino.transport({
      target:  'pino-pretty',
      options: {
        colorize:        true,
        translateTime:   'dd-mm-yyyy HH:MM:ss.l',
        ignore:          'pid,hostname',
        messageFormat:   '[{service}] {msg}',
      },
    });

// ─── Logger instance ─────────────────────────────────────────────────────────
export const logger = pino(
  {
    name:  SERVICE_NAME,
    level: process.env.LOG_LEVEL ?? 'info',
    // Base fields included in every log line
    base: { service: SERVICE_NAME },
    // ISO timestamp — consistent with Java services
    timestamp: pino.stdTimeFunctions.isoTime,
  },
  transport,
);
