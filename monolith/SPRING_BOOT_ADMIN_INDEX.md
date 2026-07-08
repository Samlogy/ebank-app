# 📚 Spring Boot Admin — Documentation Index

One page to find whatever you need about Spring Boot Admin (SBA) in the eBank Monolith. Start here if you don't know which doc to open.

---

## What is Spring Boot Admin, in one paragraph?

A monitoring dashboard for Spring Boot apps. The API registers itself with a small standalone "admin server" on startup; the server then polls the API's Actuator endpoints and renders health, JVM metrics, HTTP traffic, logs, caches, and thread dumps as a live web UI at `http://localhost:8090`. No custom code — it's wiring + configuration on top of `spring-boot-starter-actuator`.

---

## Where everything lives

| File | Purpose | Read time |
|---|---|---|
| [START_HERE.md](START_HERE.md) | Entry point — 30-second quick start | 2 min |
| **SPRING_BOOT_ADMIN_INDEX.md** | This file — navigation map | 5 min |
| [SPRING_BOOT_ADMIN_GUIDE.md](SPRING_BOOT_ADMIN_GUIDE.md) | Main guide — architecture, setup for every profile, full tab-by-tab feature walkthrough | 20 min |
| [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md) | Copy-paste start/stop commands, curl one-liners per actuator endpoint, troubleshooting table | 15 min |
| [ADMIN_MONITORING_SCENARIOS.md](ADMIN_MONITORING_SCENARIOS.md) | Real debugging playbooks: "user gets 400", "memory keeps growing", "endpoint suddenly slow" | 20 min |
| [SPRING_ADMIN_SETUP_COMPLETE.md](SPRING_ADMIN_SETUP_COMPLETE.md) | Inventory of every file/config key this feature touches, plus known limitations | 5 min |
| [`start-with-admin.sh`](start-with-admin.sh) | The automated startup script itself | N/A |
| [README.md](README.md#spring-boot-admin) | Repo-level summary (architecture diagram + endpoint table) | 2 min |
| [doc/OBSERVABILITY.md](doc/OBSERVABILITY.md#spring-boot-admin) | How SBA fits alongside Prometheus/Grafana/Tempo/Loki | 10 min |

**Total for full mastery:** ~45 minutes.

---

## Pick your path

```
                    Start here
                        │
                        ▼
        ┌───────────────┴───────────────┐
        │                               │
   New to this?                  Already know SBA?
        │                               │
        ▼                               ▼
SPRING_BOOT_ADMIN_GUIDE.md      ADMIN_QUICK_REFERENCE.md
(read the whole thing once)      (bookmark, use daily)
        │
        ▼
Debugging something right now?
        │
        ▼
ADMIN_MONITORING_SCENARIOS.md
(find your symptom, follow the steps)
```

### 🟢 New user
Read [SPRING_BOOT_ADMIN_GUIDE.md](SPRING_BOOT_ADMIN_GUIDE.md) sections 1–5 to understand what SBA is and how it's wired into this repo, then follow section 3 to get it running.

### 🟡 Getting set up right now
Jump straight to [SPRING_BOOT_ADMIN_GUIDE.md § 3 Setup](SPRING_BOOT_ADMIN_GUIDE.md#3-setup) — or just run:
```bash
./start-with-admin.sh
```

### 🟠 Daily operations (already running)
Keep [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md) open in a tab — it has every curl command and the troubleshooting table.

### 🔴 Something's wrong in prod/dev right now
Go to [ADMIN_MONITORING_SCENARIOS.md](ADMIN_MONITORING_SCENARIOS.md) and match your symptom to a scenario.

---

## Fastest path to a working dashboard

```bash
cd monolith
./start-with-admin.sh
# → http://localhost:8090   login: admin / admin
```

If it's not showing your app as registered, read [SPRING_BOOT_ADMIN_GUIDE.md § 3.8](SPRING_BOOT_ADMIN_GUIDE.md#38-client-self-registration) or the troubleshooting table in [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md#troubleshooting).
