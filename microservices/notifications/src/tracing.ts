/**
 * OpenTelemetry bootstrap — MUST be imported before any other module so that
 * auto-instrumentation can patch http/express/kafkajs before they're required.
 * Exports spans via OTLP/HTTP to Tempo, same collector every Java service uses.
 */
import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { Resource } from '@opentelemetry/resources';
import { ATTR_SERVICE_NAME, ATTR_SERVICE_VERSION } from '@opentelemetry/semantic-conventions';

const OTLP_ENDPOINT = process.env.OTEL_EXPORTER_OTLP_ENDPOINT ?? 'http://tempo:4318/v1/traces';
const SERVICE_NAME = process.env.OTEL_SERVICE_NAME ?? 'notification-service';

const sdk = new NodeSDK({
  resource: new Resource({
    [ATTR_SERVICE_NAME]: SERVICE_NAME,
    [ATTR_SERVICE_VERSION]: '1.0.0',
  }),
  traceExporter: new OTLPTraceExporter({ url: OTLP_ENDPOINT }),
  instrumentations: [
    getNodeAutoInstrumentations({
      '@opentelemetry/instrumentation-fs': { enabled: false },
      '@opentelemetry/instrumentation-http': {
        // Health/metrics polling is noise — keep Tempo focused on real traffic.
        ignoreIncomingRequestHook: (req) =>
          req.url === '/health' || req.url === '/metrics',
      },
    }),
  ],
});

sdk.start();

process.on('SIGTERM', () => {
  sdk.shutdown().catch(() => undefined);
});
