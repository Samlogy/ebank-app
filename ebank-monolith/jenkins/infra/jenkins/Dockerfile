FROM jenkins/jenkins:lts-jdk17

USER root

# ── 1. Paquets système (Chromium inclus pour Pa11y) ───────────────────────
RUN apt-get update && apt-get install -y --no-install-recommends \
    apt-transport-https \
    ca-certificates \
    curl \
    gnupg \
    lsb-release \
    gettext-base \
    python3 \
    python3-pip \
    python3-venv \
    python3.13-venv \
    chromium \
    chromium-driver \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# ── 2. Docker CLI ─────────────────────────────────────────────────────────
RUN curl -fsSL https://download.docker.com/linux/debian/gpg \
      | gpg --dearmor -o /usr/share/keyrings/docker-archive-keyring.gpg \
    && echo \
       "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/docker-archive-keyring.gpg] \
        https://download.docker.com/linux/debian $(lsb_release -cs) stable" \
       > /etc/apt/sources.list.d/docker.list \
    && apt-get update \
    && apt-get install -y --no-install-recommends docker-ce-cli \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

# ── 3. kubectl ────────────────────────────────────────────────────────────
RUN KUBECTL_VERSION=$(curl -sSL https://dl.k8s.io/release/stable.txt) \
    && curl -sSLO "https://dl.k8s.io/release/${KUBECTL_VERSION}/bin/linux/amd64/kubectl" \
    && install -o root -g root -m 0755 kubectl /usr/local/bin/kubectl \
    && rm kubectl

# ── 4. Trivy ──────────────────────────────────────────────────────────────
RUN curl -sSfL https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/install.sh \
    | sh -s -- -b /usr/local/bin

# ── 5. Hadolint ───────────────────────────────────────────────────────────
RUN curl -sSLo /usr/local/bin/hadolint \
      https://github.com/hadolint/hadolint/releases/latest/download/hadolint-Linux-x86_64 \
    && chmod +x /usr/local/bin/hadolint

# ── 6. Checkov (IaC scanner) — virtualenv pour éviter le conflit Debian packaging
RUN python3 -m venv /opt/checkov-venv \
    && /opt/checkov-venv/bin/pip install --no-cache-dir checkov \
    && ln -s /opt/checkov-venv/bin/checkov /usr/local/bin/checkov

# ── 7. Node.js 20 LTS + Newman + Pa11y ────────────────────────────────────
RUN curl -fsSL https://deb.nodesource.com/setup_20.x | bash - \
    && apt-get install -y --no-install-recommends nodejs \
    && apt-get clean && rm -rf /var/lib/apt/lists/* \
    && npm install -g --no-fund --no-audit \
        newman \
        pa11y

# Pa11y utilise le Chromium du système (évite le téléchargement de Chromium via Puppeteer)
ENV PUPPETEER_SKIP_CHROMIUM_DOWNLOAD=true
ENV PUPPETEER_EXECUTABLE_PATH=/usr/bin/chromium

# ── 8. Permissions Docker socket ──────────────────────────────────────────
RUN groupadd -f docker && usermod -aG docker jenkins

# ── 9. Plugins Jenkins pré-installés ──────────────────────────────────────
COPY plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli --plugin-file /usr/share/jenkins/ref/plugins.txt

USER jenkins

# JAVA_OPTS mis dans docker-compose pour ne pas baker dans l'image
# Le setup wizard est conservé pour la configuration initiale du compte admin
