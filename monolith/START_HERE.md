# 🚀 Spring Boot Admin — START HERE

Welcome! This is your entry point for launching and using Spring Boot Admin with the eBank Monolith.

---

## ⚡ 30-Second Quick Start

```bash
cd /home/sam/Desktop/ebank-app/monolith

# Start everything
./start-with-admin.sh

# Open in browser (once startup completes)
# http://localhost:8090
# Login: admin / admin
```

**That's it!** Your monitoring dashboard will be ready in ~30-60 seconds.

---

## 📖 What to Read

Choose your path based on what you need:

### 🟢 **I'm New** (First Time?)
→ Read [SPRING_BOOT_ADMIN_INDEX.md](SPRING_BOOT_ADMIN_INDEX.md) (5 minutes)

This file explains:
- What Spring Boot Admin is
- Where all the documentation is
- How to navigate the guides
- Learning paths for different needs

### 🟡 **I Need to Get Started** (Quick Setup)
→ Read [SPRING_BOOT_ADMIN_GUIDE.md](SPRING_BOOT_ADMIN_GUIDE.md) - "Quick Start" section (10 minutes)

This shows you:
- How to start the stack for each profile (local, dev, prod)
- How to access the Admin UI
- What each dashboard tab does
- Step-by-step features guide

### 🟠 **I Need Quick Commands** (Daily Operations)
→ Use [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md) (bookmark this!)

This includes:
- One-liner start/stop commands
- Health checks
- Curl examples for automation
- Troubleshooting decision tree

### 🔴 **I'm Debugging an Issue** (Incident Response)
→ Find your scenario in [ADMIN_MONITORING_SCENARIOS.md](ADMIN_MONITORING_SCENARIOS.md)

This covers:
- "User getting 400 error" — How to debug with logs
- "Memory keeps growing" — How to detect memory leak
- "Endpoint suddenly slow" — How to find bottleneck
- "Cache hit rate low" — How to optimize
- "Database errors" — How to diagnose
- "One user affected" — How to isolate problem

---

## 📁 Files You Now Have

| File | Purpose | Read Time |
|---|---|---|
| **START_HERE.md** | This file — entry point | 2 min |
| **SPRING_BOOT_ADMIN_INDEX.md** | Documentation index & navigation | 5 min |
| **SPRING_BOOT_ADMIN_GUIDE.md** | Main guide — features & setup | 20 min |
| **ADMIN_QUICK_REFERENCE.md** | Commands & troubleshooting | 15 min |
| **ADMIN_MONITORING_SCENARIOS.md** | Real-world debugging examples | 20 min |
| **SPRING_ADMIN_SETUP_COMPLETE.md** | What was created & status | 5 min |
| **start-with-admin.sh** | Automated startup script | N/A |

**Total reading:** ~45 minutes for complete mastery

---

## 🎯 The 3-Step Process

### Step 1: Start the Stack

```bash
./start-with-admin.sh
```

Wait for:
```
✓ Stack is running!

Services:
  Admin Server: http://localhost:8090
  Application:  http://localhost:8081
```

### Step 2: Open Admin UI

```
http://localhost:8090
Login: admin / admin
```

### Step 3: Monitor Your App

- **Health Tab** → Check if DB, Redis, disk are OK
- **HTTP Requests Tab** → Watch request latency & errors
- **JVM Tab** → Monitor memory & garbage collection
- **Loggers Tab** → Change log levels without restart

---

## 🆘 Troubleshooting

| Problem | Solution |
|---|---|
| Can't start script | Make it executable: `chmod +x start-with-admin.sh` |
| Can't reach http://localhost:8090 | Docker might not be running: `docker --version` |
| App shows "Offline" in Admin | Check logs: `docker logs ebank_app \| tail -50` |
| Don't know what to do | Read [SPRING_BOOT_ADMIN_INDEX.md](SPRING_BOOT_ADMIN_INDEX.md) |

More troubleshooting → [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md#troubleshooting)

---

## ✅ What You Can Do Now

### Monitoring
- ✅ Real-time JVM metrics (memory, threads, GC)
- ✅ HTTP request tracking (latency, errors, throughput)
- ✅ Service health checks (database, Redis, disk)
- ✅ Cache performance analysis
- ✅ Database migration history

### Debugging
- ✅ Change log levels without restart
- ✅ View live thread dumps
- ✅ Monitor endpoint performance
- ✅ Detect memory leaks
- ✅ Find slow queries

### Operations
- ✅ Start/stop with single command
- ✅ Support 3 profiles (local, dev, prod)
- ✅ Automate health checks
- ✅ Generate performance reports

---

## 📚 Documentation Structure

```
Quick Start
    ↓
READ FIRST: START_HERE.md (this file)
    ↓
Choose your path:
├─→ New user?         → SPRING_BOOT_ADMIN_INDEX.md
├─→ Setup & features? → SPRING_BOOT_ADMIN_GUIDE.md
├─→ Daily work?       → ADMIN_QUICK_REFERENCE.md
└─→ Debugging issue?  → ADMIN_MONITORING_SCENARIOS.md
```

---

## 💡 Pro Tips

1. **Bookmark [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md)** — You'll use it daily
2. **Check Health tab first** — When something seems wrong
3. **Use the script, not manual commands** — `./start-with-admin.sh` handles everything
4. **Change log levels in Admin UI** — No restart needed, change takes effect immediately
5. **Watch the metrics in real-time** — Great for understanding app behavior under load

---

## 🎓 Next Steps (In Order)

### Right Now
1. Run `./start-with-admin.sh`
2. Open http://localhost:8090
3. Login with admin/admin
4. Explore the dashboard for 5 minutes

### In the Next Hour
1. Read [SPRING_BOOT_ADMIN_INDEX.md](SPRING_BOOT_ADMIN_INDEX.md)
2. Skim [SPRING_BOOT_ADMIN_GUIDE.md](SPRING_BOOT_ADMIN_GUIDE.md)
3. Try changing a logger level in Admin UI

### This Week
1. Read [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md) fully
2. Study one scenario from [ADMIN_MONITORING_SCENARIOS.md](ADMIN_MONITORING_SCENARIOS.md)
3. Practice with the curl commands
4. Bookmark all 4 docs in your browser

### For Production
1. Review [SPRING_BOOT_ADMIN_GUIDE.md](SPRING_BOOT_ADMIN_GUIDE.md) → "Best Practices"
2. Set up HTTPS and change admin password
3. Restrict network access
4. Integrate with your observability stack

---

## 📞 Questions?

| Question | Answer |
|---|---|
| How do I start? | `./start-with-admin.sh` |
| Where is Admin UI? | http://localhost:8090 |
| What's the password? | admin / admin |
| How do I stop? | `./start-with-admin.sh stop` |
| What if I'm stuck? | Read [SPRING_BOOT_ADMIN_INDEX.md](SPRING_BOOT_ADMIN_INDEX.md) |
| Need a command? | Check [ADMIN_QUICK_REFERENCE.md](ADMIN_QUICK_REFERENCE.md) |
| Debugging something? | Find it in [ADMIN_MONITORING_SCENARIOS.md](ADMIN_MONITORING_SCENARIOS.md) |

---

## 🎉 Ready to Go!

Everything is set up and documented. 

**Next action:** Run `./start-with-admin.sh` and explore!

---

### Files in This Directory

```
monolith/
├── 📄 START_HERE.md                    ← You are here
├── 📄 SPRING_BOOT_ADMIN_INDEX.md       ← Read next
├── 📄 SPRING_BOOT_ADMIN_GUIDE.md       ← Main guide
├── 📄 ADMIN_QUICK_REFERENCE.md         ← Daily reference
├── 📄 ADMIN_MONITORING_SCENARIOS.md    ← Debugging guide
├── 📄 SPRING_ADMIN_SETUP_COMPLETE.md   ← Summary
├── 🚀 start-with-admin.sh              ← Run this
│
├── src/                # Source code
├── admin/              # Spring Boot Admin server
├── docker-compose.yml  # Services configuration
├── pom.xml            # Maven configuration
└── ...
```

---

**Status:** ✅ Ready to use
**Created:** June 8, 2026
**Version:** 1.0 Complete

