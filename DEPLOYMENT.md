# ShopEase — AWS Deployment Guide

This document covers the full manual deployment of ShopEase backend to AWS (Hyderabad region `ap-south-2`) using EC2, RDS, ElastiCache, ALB, and Docker Hub.

---

## Live URLs

| Endpoint | URL |
|----------|-----|
| API Base | `http://shopease-alb-848970636.ap-south-2.elb.amazonaws.com/api` |
| Health Check | `http://shopease-alb-848970636.ap-south-2.elb.amazonaws.com/api/actuator/health` |
| Swagger UI | `http://shopease-alb-848970636.ap-south-2.elb.amazonaws.com/api/swagger-ui.html` |

---

## AWS Services Used

| Service | Purpose | Config |
|---------|---------|--------|
| **EC2** (t3.micro) | Runs the Spring Boot Docker container | Amazon Linux 2023, `ap-south-2` |
| **RDS PostgreSQL 15** (db.t3.micro) | Primary database | `ecommerce_prod` DB, 20GB gp2 |
| **ElastiCache Redis 7** (cache.t3.micro) | Session cache, product cache | Single node |
| **ALB** (Application Load Balancer) | Routes internet traffic to EC2, health checks | Port 80 → EC2:8080 |
| **ECR** | Container registry (created, image on Docker Hub for now) | `ap-south-2` |
| **Docker Hub** | Public image registry | `asampavan14322/shopease-app:latest` |
| **VPC** (default) | Network isolation | `vpc-058024083aaba5e61`, `ap-south-2` |
| **Security Groups** | Firewall rules per layer | 4 groups — ALB, EC2, RDS, Redis |

---

## Architecture

```
Internet
    │
    ▼
ALB  (shopease-alb-848970636.ap-south-2.elb.amazonaws.com)
 Port 80 → forwards to EC2:8080
    │
    ▼
EC2 t3.micro  (40.192.1.89)
 Docker → asampavan14322/shopease-app:latest
    │
    ├──▶ RDS PostgreSQL (private)
    │    shopease-postgres.c9iii468arxx.ap-south-2.rds.amazonaws.com:5432
    │
    └──▶ ElastiCache Redis (private)
         shopease-redis.re3e3w.0001.aps2.cache.amazonaws.com:6379
```

---

## Security Groups

| Group | Name | Inbound Rules |
|-------|------|---------------|
| `sg-00596e757d6896690` | shopease-alb-sg | 80, 443 from 0.0.0.0/0 |
| `sg-0d1fe0784a5970395` | shopease-ec2-sg | 8080 from ALB SG only, 22 from 0.0.0.0/0 |
| `sg-0ad993668c97f1c53` | shopease-rds-sg | 5432 from EC2 SG only |
| `sg-01fb6362b4e4e05ed` | shopease-redis-sg | 6379 from EC2 SG only |

> RDS and Redis are never exposed to the internet — only reachable from EC2.

---

## Step-by-Step: What Was Done

### Step 1 — IAM Setup

Created IAM user `shopease-deploy` with `AdministratorAccess` policy.
Generated access keys and configured AWS CLI:

```bash
aws configure
# Access Key ID:     AKIA...
# Secret Access Key: ...
# Default region:    ap-south-2
# Output format:     json

# Verify
aws sts get-caller-identity
```

### Step 2 — Security Groups

Created 4 security groups in the default VPC with strict least-privilege rules.
Each layer can only talk to the layer directly below it:

```
Internet → ALB → EC2 → RDS/Redis
```

```bash
# ALB SG — internet traffic
aws ec2 create-security-group --group-name shopease-alb-sg ...
aws ec2 authorize-security-group-ingress --group-id <ALB_SG> --port 80 --cidr 0.0.0.0/0
aws ec2 authorize-security-group-ingress --group-id <ALB_SG> --port 443 --cidr 0.0.0.0/0

# EC2 SG — only ALB can reach app port
aws ec2 create-security-group --group-name shopease-ec2-sg ...
aws ec2 authorize-security-group-ingress --group-id <EC2_SG> --port 8080 --source-group <ALB_SG>
aws ec2 authorize-security-group-ingress --group-id <EC2_SG> --port 22 --cidr 0.0.0.0/0

# RDS SG — only EC2 can reach Postgres
aws ec2 create-security-group --group-name shopease-rds-sg ...
aws ec2 authorize-security-group-ingress --group-id <RDS_SG> --port 5432 --source-group <EC2_SG>

# Redis SG — only EC2 can reach Redis
aws ec2 create-security-group --group-name shopease-redis-sg ...
aws ec2 authorize-security-group-ingress --group-id <REDIS_SG> --port 6379 --source-group <EC2_SG>
```

### Step 3 — RDS PostgreSQL

Created a subnet group across all 3 AZs, then launched the DB instance:

```bash
aws rds create-db-subnet-group \
  --db-subnet-group-name shopease-rds-subnet-group \
  --subnet-ids subnet-07326a102696f4744 subnet-0ba40d80941fdfcd6 subnet-04ba0cf9315f1d2d9

aws rds create-db-instance \
  --db-instance-identifier shopease-postgres \
  --db-instance-class db.t3.micro \
  --engine postgres \
  --engine-version 15 \
  --master-username postgres \
  --master-user-password ShopEase2024Prod \
  --db-name ecommerce_prod \
  --allocated-storage 20 \
  --vpc-security-group-ids <RDS_SG> \
  --db-subnet-group-name shopease-rds-subnet-group \
  --region ap-south-2
```

Takes ~5-7 minutes to become `available`. Check status:

```bash
aws rds describe-db-instances \
  --db-instance-identifier shopease-postgres \
  --query "DBInstances[0].[DBInstanceStatus,Endpoint.Address]" --output text
```

### Step 4 — ElastiCache Redis

```bash
aws elasticache create-cache-subnet-group \
  --cache-subnet-group-name shopease-redis-subnet-group \
  --subnet-ids subnet-07326a102696f4744 subnet-0ba40d80941fdfcd6 subnet-04ba0cf9315f1d2d9

aws elasticache create-cache-cluster \
  --cache-cluster-id shopease-redis \
  --cache-node-type cache.t3.micro \
  --engine redis \
  --engine-version 7.0 \
  --num-cache-nodes 1 \
  --cache-subnet-group-name shopease-redis-subnet-group \
  --security-group-ids <REDIS_SG> \
  --region ap-south-2
```

Check status:

```bash
aws elasticache describe-cache-clusters \
  --cache-cluster-id shopease-redis \
  --show-cache-node-info \
  --query "CacheClusters[0].[CacheClusterStatus,CacheNodes[0].Endpoint.Address]" --output text
```

### Step 5 — EC2 Instance

Created a key pair, then launched an Amazon Linux 2023 t3.micro with Docker installed via user-data:

```bash
# Create key pair — save the .pem file
aws ec2 create-key-pair --key-name shopease-key --region ap-south-2 \
  --query "KeyMaterial" --output text > shopease-key.pem

chmod 400 shopease-key.pem

# Launch EC2 with Docker pre-installed
aws ec2 run-instances \
  --image-id ami-0960ac155146d21bd \
  --instance-type t3.micro \
  --key-name shopease-key \
  --security-group-ids <EC2_SG> \
  --user-data '#!/bin/bash
yum update -y
yum install -y docker
systemctl start docker
systemctl enable docker
usermod -aG docker ec2-user'
```

SSH into EC2:

```bash
ssh -i shopease-key.pem ec2-user@40.192.1.89
```

### Step 6 — Docker Image

Built the Spring Boot app image locally and pushed to Docker Hub:

```bash
# Build
docker build -t shopease-app .

# Tag
docker tag shopease-app:latest asampavan14322/shopease-app:latest

# Push
docker push asampavan14322/shopease-app:latest
```

On EC2, pulled and ran the container with all environment variables pointing to RDS and Redis:

```bash
docker run -d \
  --name shopease \
  -p 8080:8080 \
  --restart unless-stopped \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://shopease-postgres.c9iii468arxx.ap-south-2.rds.amazonaws.com:5432/ecommerce_prod \
  -e DB_USERNAME=postgres \
  -e DB_PASSWORD=ShopEase2024Prod \
  -e REDIS_HOST=shopease-redis.re3e3w.0001.aps2.cache.amazonaws.com \
  -e REDIS_PORT=6379 \
  -e JWT_SECRET=shopease-super-secret-jwt-key-for-production-2024 \
  -e MANAGEMENT_HEALTH_MAIL_ENABLED=false \
  -e SPRING_JPA_HIBERNATE_DDL_AUTO=update \
  -e ALLOWED_ORIGINS=http://40.192.1.89:8080,http://localhost:5173 \
  -e MAIL_USERNAME=noreply@shopease.com \
  -e MAIL_PASSWORD=disabled \
  asampavan14322/shopease-app:latest

# Watch logs
docker logs -f shopease
```

### Step 7 — ALB (Application Load Balancer)

Created a target group, registered EC2, then created the ALB with a listener:

```bash
# Target group
aws elbv2 create-target-group \
  --name shopease-tg \
  --protocol HTTP \
  --port 8080 \
  --vpc-id vpc-058024083aaba5e61 \
  --health-check-path /api/actuator/health

# Register EC2 to target group
aws elbv2 register-targets \
  --target-group-arn <TG_ARN> \
  --targets Id=i-09e7f9c7ff0cf5058

# Create ALB
aws elbv2 create-load-balancer \
  --name shopease-alb \
  --subnets subnet-07326a102696f4744 subnet-0ba40d80941fdfcd6 subnet-04ba0cf9315f1d2d9 \
  --security-groups <ALB_SG>

# Create listener: port 80 → forward to target group
aws elbv2 create-listener \
  --load-balancer-arn <ALB_ARN> \
  --protocol HTTP \
  --port 80 \
  --default-actions Type=forward,TargetGroupArn=<TG_ARN>
```

---

## Pending

| Step | What |
|------|------|
| S3 + CloudFront | Deploy React frontend |
| GitHub Actions | CI/CD — auto build + deploy on git push |
| HTTPS / ACM | SSL certificate on ALB |
| Route 53 | Custom domain instead of ALB DNS |

---

## Common Problems & Fixes

### 1. `Could not resolve placeholder 'ALLOWED_ORIGINS'`
**Cause:** Prod profile has no defaults — every env var must be explicitly passed.  
**Fix:** Add `-e ALLOWED_ORIGINS=...` to the `docker run` command.

### 2. `Could not resolve placeholder 'MAIL_USERNAME'`
**Cause:** Same as above — prod profile requires all mail vars.  
**Fix:** Add `-e MAIL_USERNAME=noreply@shopease.com -e MAIL_PASSWORD=disabled` and `-e MANAGEMENT_HEALTH_MAIL_ENABLED=false`.

### 3. Schema validation error on first start (`missing table [addresses]`)
**Cause:** Prod profile uses `ddl-auto=validate` but fresh RDS has no tables.  
**Fix:** Pass `-e SPRING_JPA_HIBERNATE_DDL_AUTO=update` on first boot. After tables are created, remove this and let validate run normally.

### 4. SSH permission denied (`Permission denied (publickey)`)
**Cause:** Wrong path syntax in Git Bash (backslashes vs forward slashes) or wrong permissions on .pem.  
**Fix:**
```bash
chmod 400 ~/Downloads/shopease-key.pem
ssh -i ~/Downloads/shopease-key.pem ec2-user@40.192.1.89
```

### 5. AWS CLI path conversion issue in Git Bash
**Cause:** Git Bash converts `/api/actuator/health` to `C:/Program Files/Git/api/actuator/health`.  
**Fix:** Prefix the command with `MSYS_NO_PATHCONV=1`:
```bash
MSYS_NO_PATHCONV=1 aws elbv2 create-target-group --health-check-path /api/actuator/health ...
```

### 6. Port 8080 not reachable from browser but ALB health check passes
**Cause:** EC2 security group only allows 8080 from ALB SG, not the internet directly. This is correct.  
**Fix:** Always hit the ALB DNS, not EC2 IP directly. Test from inside EC2 using `curl http://localhost:8080/...`.

### 7. RDS password rejected (`InvalidParameterValue`)
**Cause:** RDS passwords cannot contain `@`, `"`, `/`, or spaces.  
**Fix:** Use alphanumeric passwords only: `ShopEase2024Prod`.

### 8. Container crash-loops after `docker run`
**Fix:** Check logs first before anything else:
```bash
docker logs shopease 2>&1 | grep -E 'Could not|Caused by|ERROR|Exception'
```

### 9. Docker Desktop not running
**Cause:** `open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`  
**Fix:** Open Docker Desktop from Start menu, wait for the whale tray icon to become solid, then retry.

### 10. To update the app with a new image version
```bash
# On your machine — build and push new image
docker build -t asampavan14322/shopease-app:latest .
docker push asampavan14322/shopease-app:latest

# SSH into EC2 and pull + restart
ssh -i shopease-key.pem ec2-user@40.192.1.89
docker pull asampavan14322/shopease-app:latest
docker stop shopease && docker rm shopease
docker run -d --name shopease ... (same run command as above)
```

---

## Resource IDs (ap-south-2)

| Resource | ID / ARN |
|----------|----------|
| VPC | `vpc-058024083aaba5e61` |
| EC2 Instance | `i-09e7f9c7ff0cf5058` |
| EC2 Public IP | `40.192.1.89` |
| RDS Endpoint | `shopease-postgres.c9iii468arxx.ap-south-2.rds.amazonaws.com` |
| Redis Endpoint | `shopease-redis.re3e3w.0001.aps2.cache.amazonaws.com` |
| ALB DNS | `shopease-alb-848970636.ap-south-2.elb.amazonaws.com` |
| ALB ARN | `arn:aws:elasticloadbalancing:ap-south-2:924263791004:loadbalancer/app/shopease-alb/e7aff52e7ccf2d19` |
| Target Group ARN | `arn:aws:elasticloadbalancing:ap-south-2:924263791004:targetgroup/shopease-tg/4e33bb582d9aaf5c` |
| ECR Repo | `924263791004.dkr.ecr.ap-south-2.amazonaws.com/shopease-app` |
| Docker Hub Image | `asampavan14322/shopease-app:latest` |
| Key Pair | `shopease-key` → `~/Downloads/shopease-key.pem` |
