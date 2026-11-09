# ADR 0009: HTTPS through CloudFront, with a fixed server address

**Date:** 2026-11-07 · **Status:** Accepted

## Context
The live site was served over plain `http://` from the instance's EC2 hostname. That caused two problems:
- Browsers only allow location sharing (drivers' GPS), clipboard access and `crypto.randomUUID` on secure (https)
  pages. Drivers couldn't share their location on the live site.
- The hostname changed every time the server was stopped and started, so links went stale.

We have no domain name, so we can't get our own TLS certificate, and certificates on the instance itself
(e.g. Let's Encrypt) need a domain as well.

## Decision
- **Elastic IP:** the server gets a fixed public IP. Its EC2 hostname (`ec2-<ip>.<region>.compute.amazonaws.com`)
  is then fixed too.
- **CloudFront** in front of the server, using CloudFront's own `*.cloudfront.net` certificate. Viewers are
  redirected from http to https; CloudFront talks to the server over plain http on port 80.
- **No caching of the API:** the default behaviour uses a cache policy with TTL 0 (Authorization header and
  query strings in the key) and forwards all viewer headers. Only hashed `*.js`/`*.css` files and `/media/*` use
  CloudFront's managed `CachingOptimized` policy.
- **nginx** passes `CloudFront-Forwarded-Proto` on to the backend as `X-Forwarded-Proto`, and sends
  `Strict-Transport-Security` only when the viewer really came in over https.
- **Swagger** advertises a relative server (`/`), so "Try it out" calls whatever address the page was opened on
  (localhost, plain http or CloudFront) instead of a service's internal Docker hostname.

## Consequences
- The site has a stable `https://<id>.cloudfront.net` address (stack output `WebsiteUrl`) that survives stop/start.
- An Elastic IP costs about $3.60/month, including while the server is stopped (AWS charges for every public
  IPv4 address; the running server already paid this for its automatic IP). CloudFront stays within its
  always-free tier for a demo.
- The server's port 80 is still reachable directly over plain http. That's useful for debugging, and the
  deploy's smoke test uses it.
- A custom domain later only needs an ACM certificate (in us-east-1) and an alias on the distribution.
