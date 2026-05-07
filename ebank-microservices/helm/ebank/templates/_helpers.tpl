{{/*
_helpers.tpl — Shared template snippets reused across all service templates.
These are "named templates" (partials). They are called with `include`.

HOW TO USE in a template:
  {{ include "ebank.labels" . | nindent 4 }}
  {{ include "ebank.vaultEnv" . | nindent 12 }}
*/}}

{{/* Chart name, trimmed to 63 chars (K8s label limit) */}}
{{- define "ebank.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Full name: "<release>-<chart>" or nameOverride */}}
{{- define "ebank.fullname" -}}
{{- if .Values.fullnameOverride }}
  {{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
  {{- $name := default .Chart.Name .Values.nameOverride }}
  {{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}

{{/* Chart label value: "<name>-<version>" */}}
{{- define "ebank.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Standard labels applied to every K8s resource.
helm.sh/chart      → enables "helm list" to show chart version
app.kubernetes.io/ → K8s standard labels for tooling (Lens, Argo, etc.)
*/}}
{{- define "ebank.labels" -}}
helm.sh/chart: {{ include "ebank.chart" . }}
{{ include "ebank.selectorLabels" . }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: ebank
environment: {{ .Values.global.env }}
{{- end }}

{{/*
Selector labels — used in both Deployment.spec.selector.matchLabels
and Service.spec.selector.  Must be IMMUTABLE after first deploy.
WHY separate from ebank.labels: selectors cannot change on rolling updates.
*/}}
{{- define "ebank.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ebank.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Resolve the image tag: use per-service tag if set, else fall back to global.imageTag.
Call as: {{ include "ebank.imageTag" (dict "service" .Values.auth "global" .Values.global) }}
*/}}
{{- define "ebank.imageTag" -}}
{{- if .service.tag }}{{ .service.tag }}{{- else }}{{ .global.imageTag }}{{- end }}
{{- end }}

{{/*
Full image reference: "<registry>/<image>:<tag>"
Call as: {{ include "ebank.image" (dict "service" .Values.auth "global" .Values.global) }}
*/}}
{{- define "ebank.image" -}}
{{- $tag := include "ebank.imageTag" . }}
{{- if .global.registry }}
  {{- printf "%s/%s:%s" .global.registry .service.image $tag }}
{{- else }}
  {{- printf "%s:%s" .service.image $tag }}
{{- end }}
{{- end }}

{{/*
Vault environment variables injected into every Spring Boot service.
Services use Spring Cloud Vault Config to fetch ALL their config from Vault.
VAULT_URI  → Spring Cloud Vault property (spring.cloud.vault.uri)
VAULT_TOKEN → read from K8s Secret to avoid committing tokens to Git
*/}}
{{- define "ebank.vaultEnv" -}}
- name: VAULT_URI
  value: {{ .Values.global.vault.addr | quote }}
- name: VAULT_TOKEN
  valueFrom:
    secretKeyRef:
      name: {{ .Values.global.vault.tokenSecretName }}
      key: {{ .Values.global.vault.tokenSecretKey }}
{{- end }}

{{/*
Spring Boot profile env var.
SPRING_PROFILES_ACTIVE controls which Vault path is read:
  secret/ebank/<service>/prod  OR  secret/ebank/<service>/recf
*/}}
{{- define "ebank.springProfileEnv" -}}
- name: SPRING_PROFILES_ACTIVE
  value: {{ .Values.global.env | quote }}
{{- end }}

{{/*
InitContainer that blocks a Spring service from starting until Vault is reachable
and its secrets are seeded (vault-init pre-install hook must have completed first).

WHY: All Spring services use Spring Cloud Vault Config (spring.config.import: "optional:vault://").
     They connect to Vault directly at startup to fetch ALL their configuration.
     If Vault is not ready, the Spring context fails to initialize.
     There is NO config-server in this project — Vault IS the config source.

Uses curlimages/curl (tiny, ~6MB image).
*/}}
{{- define "ebank.waitForVault" -}}
- name: wait-vault
  image: curlimages/curl:8.7.1
  command:
    - /bin/sh
    - -c
    - |
      echo "Waiting for Vault at {{ .Values.global.vault.addr }}..."
      until curl -sf {{ .Values.global.vault.addr }}/v1/sys/health; do
        echo "  Vault not ready, retrying in 5s..."
        sleep 5
      done
      echo "Vault is ready."
  resources:
    requests:
      cpu: "10m"
      memory: "16Mi"
    limits:
      cpu: "50m"
      memory: "32Mi"
{{- end }}

{{/*
Computed PostgreSQL host: uses global override if set, else Bitnami chart default.
Bitnami chart names its service: <release>-postgresql
*/}}
{{- define "ebank.postgresHost" -}}
{{- if .Values.global.services.postgresHost }}
  {{- .Values.global.services.postgresHost }}
{{- else }}
  {{- printf "%s-postgresql" .Release.Name }}
{{- end }}
{{- end }}

{{/*
Computed MongoDB host
*/}}
{{- define "ebank.mongoHost" -}}
{{- if .Values.global.services.mongoHost }}
  {{- .Values.global.services.mongoHost }}
{{- else }}
  {{- printf "%s-mongodb" .Release.Name }}
{{- end }}
{{- end }}

{{/*
Computed Redis host
*/}}
{{- define "ebank.redisHost" -}}
{{- if .Values.global.services.redisHost }}
  {{- .Values.global.services.redisHost }}
{{- else }}
  {{- printf "%s-redis-master" .Release.Name }}
{{- end }}
{{- end }}

{{/*
Computed Kafka bootstrap servers
*/}}
{{- define "ebank.kafkaHost" -}}
{{- if .Values.global.services.kafkaHost }}
  {{- .Values.global.services.kafkaHost }}
{{- else }}
  {{- printf "%s-kafka" .Release.Name }}
{{- end }}
{{- end }}

