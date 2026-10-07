# Deploying to a VPS (Hetzner Cloud)

The whole stack runs with Docker Compose on one small server. [Caddy](https://caddyserver.com) sits in front and gets
an HTTPS certificate automatically; only ports 80 and 443 are open. PostgreSQL, the backend and nginx are reachable
only inside the Docker network.

```
internet ──443──> caddy ──> frontend (nginx: SPA + /api proxy) ──> backend (Spring Boot) ──> postgres
```

Without a domain of our own, the server gets a free hostname from [sslip.io](https://sslip.io): the IP address with
dashes, e.g. `65-21-10-4.sslip.io`. To move to a real domain later, point the domain at the server, change
`SITE_ADDRESS` and restart Caddy.

## 1. Create the server

1. Create an account at [Hetzner Cloud](https://console.hetzner.com) and a project.
2. Add your SSH public key (Project → Security → SSH keys). On a Mac: `pbcopy < ~/.ssh/id_ed25519.pub` (or `id_rsa.pub`) copies it; if you have none, create one
   with `ssh-keygen -t ed25519`.
3. Create a server:
   - **Location:** Falkenstein, Nuremberg or Helsinki
   - **Image:** Ubuntu 24.04
   - **Type:** Shared vCPU → x86 → **CX23** (2 vCPU, 4 GB RAM; enough for the whole stack)
   - **Networking:** public IPv4 and IPv6
   - **SSH key:** the one from step 2
   - **Firewall:** create one with inbound **TCP 22, TCP 80, TCP 443 and UDP 443**, nothing else
4. Note the server's IPv4 address.

Billing is per hour, capped per month. Deleting the server stops the billing.

## 2. Install Docker and get the code

```bash
ssh root@<IP>

apt update && apt upgrade -y
curl -fsSL https://get.docker.com | sh

git clone https://github.com/B6sse/battlefront-balancer.git
cd battlefront-balancer
```

## 3. Configure

```bash
# Backend and database settings, with a random database password and secure session cookies
cp backend/.env.example backend/.env
sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -hex 24)|; s|^SESSION_COOKIE_SECURE=.*|SESSION_COOKIE_SECURE=true|" backend/.env

# The public hostname for Caddy (sslip.io name from the server's IPv4 address)
echo "SITE_ADDRESS=$(curl -4 -s https://ifconfig.me | tr . -).sslip.io" > .env
cat .env
```

Both `.env` files are git-ignored. The database password is used when the database is created the first time;
changing it later requires changing it in PostgreSQL too.

## 4. Copy the seed data (from your own machine)

The seed contains user password hashes and is never in git. Copy it straight to the server:

```bash
# on your Mac, in the repository
ssh root@<IP> mkdir -p /root/battlefront-balancer/docker/postgres/local
scp docker/postgres/local/seed.sql root@<IP>:/root/battlefront-balancer/docker/postgres/local/
```

## 5. Start

```bash
# on the server, in /root/battlefront-balancer
alias dc='docker compose -f docker-compose.yml -f docker-compose.prod.yml'
echo "alias dc='docker compose -f docker-compose.yml -f docker-compose.prod.yml'" >> ~/.bashrc

dc up -d --build                 # the first build takes 5–10 minutes
dc logs -f backend               # wait for "Started BattlefrontBalancerApplicationKt", then Ctrl+C
```

Flyway creates the schema on the backend's first start. Then load the seed data once:

```bash
dc exec -T postgres sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < docker/postgres/local/seed.sql
rm docker/postgres/local/seed.sql   # optional: it is in the database now
```

Open `https://<SITE_ADDRESS>`. The first request can take a few seconds while Caddy gets the certificate. Log in with
an existing account from the seed, and create host tokens on the Admin page.

## Everyday commands

```bash
dc ps                            # status
dc logs -f backend               # backend log (also: caddy, frontend, postgres)
git pull && dc up -d --build     # deploy a new version
dc restart caddy                 # after changing SITE_ADDRESS in .env
```

### Backup

```bash
dc exec -T postgres sh -c 'pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB"' > backup-$(date +%F).sql
# copy it to your Mac:  scp root@<IP>:/root/battlefront-balancer/backup-YYYY-MM-DD.sql .
```

### Moving to another server

On the new server, follow steps 1–3, then start only the database and restore the backup before starting the rest
(the dump includes the schema and Flyway's history):

```bash
dc up -d postgres
dc exec -T postgres sh -c 'psql -q -U "$POSTGRES_USER" -d "$POSTGRES_DB"' < backup-YYYY-MM-DD.sql
dc up -d --build
```

Then update the address in each host's Auric settings (or point the domain at the new server).

## Troubleshooting

- **No certificate / browser warning:** check `dc logs caddy`. Ports 80 and 443 must be open in the Hetzner firewall,
  and `SITE_ADDRESS` must resolve to this server.
- **Backend does not start:** `dc logs backend`. A database password mismatch usually means `backend/.env` changed
  after the database volume was created.
- **Times are in Oslo time:** the backend sets `Europe/Oslo` itself; the server's own clock can stay on UTC.
