# Phase 11 — Email & Notifications

## Objective
Send transactional emails (welcome, order confirmation, cancellation) asynchronously so email I/O never blocks the HTTP response.

---

## What We Built
| File | Purpose |
|---|---|
| `notification/EmailService.java` | Core email logic — `@Async` methods per email type |
| `config/AsyncConfig.java` | Thread pool for async tasks + `@EnableAsync` |
| `templates/emails/welcome.html` | Thymeleaf welcome email |
| `templates/emails/order-confirmation.html` | Thymeleaf order confirmation with item table |
| `templates/emails/order-cancellation.html` | Thymeleaf cancellation notice |
| `auth/AuthService.java` | Wired: sends welcome on register |
| `order/OrderService.java` | Wired: sends confirmation on place, cancellation on cancel |

## Emails Sent Automatically
| Trigger | Email |
|---|---|
| `POST /api/auth/register` | Welcome email |
| `POST /api/orders` (place order) | Order confirmation with item list + total |
| `DELETE /api/orders/{id}/cancel` | Order cancellation notice |
| Low stock (admin alert) | Direct to admin Gmail |

---

## Concepts Introduced

### Why @Async? — The Blocking Problem

```
Email sending path:
  1. TCP connect to smtp.gmail.com:587
  2. STARTTLS handshake
  3. SMTP AUTH (login)
  4. DATA transfer (email content)
  5. QUIT
  Total: 200ms – 3000ms depending on network

Without @Async (synchronous):
  POST /api/orders
    → placeOrder()
    → stock deducted, order saved           ← 20ms DB
    → sendOrderConfirmation()               ← 1500ms SMTP
    → return 201 Created                    ← user waited 1.5s extra

With @Async:
  POST /api/orders
    → placeOrder()
    → stock deducted, order saved           ← 20ms DB
    → emailService.sendOrderConfirmation()  ← schedules task, returns immediately
    → return 201 Created                    ← user gets response NOW
    (background thread sends email a second later)
```

### How @Async Works Internally

```
Without @Async: method runs in the HTTP request thread
With @Async:
  Spring wraps EmailService in an AOP proxy
  When you call emailService.sendWelcome(user):
    1. Proxy intercepts the call
    2. Submits the actual method to a ThreadPoolTaskExecutor
    3. Returns immediately to the caller
    4. Executor runs sendWelcome() in a background thread (email-1, email-2...)
    5. HTTP request thread continues, returns the response

Key requirement: @EnableAsync must be on a config class.
Without it: @Async is silently ignored — methods run synchronously.
```

### Thread Pool — Why Not Use SimpleAsyncTaskExecutor?

```
Default (no config): Spring uses SimpleAsyncTaskExecutor
  → Creates a NEW thread for every @Async call
  → 1000 orders in 1 minute = 1000 threads spawned
  → Thread creation is expensive (512KB stack each)
  → OutOfMemoryError or OS thread limit hit

Our ThreadPoolTaskExecutor:
  corePoolSize  = 2    → 2 threads always ready
  maxPoolSize   = 5    → never more than 5 email threads
  queueCapacity = 100  → queue 100 pending emails if 5 threads busy
  Thread reuse: after an email is sent, thread goes back to pool

Named threads (prefix="email-"):
  Thread dump shows: email-1, email-2 → instantly identifiable
  Without naming: pool-3-thread-7 → meaningless
```

### Thymeleaf — HTML Email Templates

```
Why templates instead of String concatenation?
  String concat:
    String html = "<h1>Hi " + user.getFirstName() + "</h1>"
                + "<p>Order #" + order.getId() + "</p>";
    → Not maintainable, no designer can edit it, XSS risk

  Thymeleaf template:
    <h1>Hi <span th:text="${firstName}">Customer</span></h1>
    <p>Order #<span th:text="${orderId}">0</span></p>
    → Designer-editable HTML file
    → Thymeleaf escapes variables (XSS prevention)
    → Variables injected via Context object in Java

Context = variable map passed to the template:
  Context ctx = new Context();
  ctx.setVariable("firstName", user.getFirstName());
  ctx.setVariable("orderId", order.getId());
  String html = templateEngine.process("emails/order-confirmation", ctx);
```

### MimeMessage vs SimpleMailMessage

```
SimpleMailMessage:
  mailSender.send(new SimpleMailMessage()
      .to("alice@test.com")
      .subject("Hello")
      .text("Plain text only")
  );
  → Text only, no HTML, no attachments

MimeMessage:
  MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
  helper.setText(htmlBody, true); // true = isHtml
  → Full HTML, images (inline attachments), file attachments

Use MimeMessage for all transactional emails — users expect styled HTML emails.
```

### Gmail App Password — Not Your Real Password

```
Google blocks "less secure app access" by default.
You cannot use your Gmail account password directly.

Setup:
  1. Enable 2-Step Verification on Google Account
  2. Go to: Google Account → Security → App passwords
  3. Create app password for "Mail" on "Windows Computer"
  4. Get 16-character password: e.g., "abcd efgh ijkl mnop"
  5. Use this (without spaces) as MAIL_PASSWORD env variable

In application.properties:
  spring.mail.password=${MAIL_PASSWORD:your-app-password-here}

Why env variable, not hardcoded?
  Never commit real credentials to git.
  Anyone who reads the code/git history gets access to your email.
```

### Email Failure Handling — Never Fail the Order

```java
@Async
public void sendOrderConfirmation(User user, Order order) {
    try {
        // ... send email
    } catch (Exception e) {
        log.error("Failed to send email: {}", e.getMessage());
        // DO NOT re-throw — email failure must never fail the order
    }
}
```

Why try-catch instead of letting it propagate?
  The order is ALREADY committed to DB before this @Async method runs.
  Email is a notification — if Gmail is down, the order still happened.
  Fail silently, log the error.
  Production: push to a retry queue (Kafka/RabbitMQ — Phase 17).
```

---

## Setting Up Gmail SMTP

```bash
# Set as environment variable (never hardcode)
# Windows PowerShell:
$env:MAIL_PASSWORD = "your-16-char-app-password"

# Or create application-dev.properties (gitignored):
spring.mail.password=your-app-password
```

---

## Common Bugs

| Bug | Cause | Fix |
|---|---|---|
| `@Async` not working (runs sync) | `@EnableAsync` missing | On `AsyncConfig` class |
| `AuthenticationFailedException` | Wrong Gmail password | Use App Password, not account password |
| `MailSendException: Connection refused` | Gmail SMTP blocked | Enable STARTTLS, check port 587 open |
| Template not found | Wrong path | Must be at `templates/emails/name.html` — matches `process("emails/name", ctx)` |
| `NullPointerException` in template | Variable not set in Context | Check `ctx.setVariable("key", value)` for every `${key}` in template |
| Email sent even on test | `@Async` submits eagerly | Use `@MockBean EmailService` in tests or configure test mail properties |

---

## Interview Questions

**Q: What is `@Async` and how does it work?**
> `@Async` makes a method run in a separate thread from the caller's thread. Spring wraps the bean in an AOP proxy; when the annotated method is called, the proxy submits it to a thread pool and returns immediately to the caller. The HTTP response returns without waiting for the method to complete. Requires `@EnableAsync` on a config class.

**Q: What is a thread pool and why is it better than creating new threads?**
> A thread pool pre-creates N threads and reuses them across tasks. Thread creation costs OS-level stack allocation (~512KB each). Unbounded thread creation (SimpleAsyncTaskExecutor) under load can exhaust OS limits or cause OOM. A pool limits concurrency and eliminates creation overhead by recycling threads after each task.

**Q: Why should email never block the HTTP response?**
> SMTP connections depend on external servers (Gmail, SendGrid). Their latency (200ms–3s) is outside your control. Blocking the HTTP thread on email I/O means your API response time is at the mercy of Gmail's availability. With `@Async`, the response returns after your DB operations (~20ms), email happens independently.

**Q: What is the difference between `MimeMessage` and `SimpleMailMessage`?**
> `SimpleMailMessage` supports plain text only. `MimeMessage` supports multipart content: HTML, inline images, and file attachments. All transactional emails should use `MimeMessage` — users expect styled HTML, not raw text.

**Q: What is a Gmail App Password and why is it needed?**
> Google disabled "less secure app access" to prevent credential theft. An App Password is a 16-character password generated specifically for a single application. It can be revoked independently without changing your Google account password. Required when your app needs to authenticate to Gmail SMTP programmatically.

---

## MFAQ

**Do I need to restart the app to see template changes?**
DevTools hot-reload handles `.java` files. For Thymeleaf templates, changes are picked up immediately if `spring.thymeleaf.cache=false` (set by DevTools in dev mode). No restart needed for template edits.

**What if I want to use SendGrid or AWS SES instead of Gmail?**
Change only the SMTP properties in `application.properties`:
- SendGrid: `host=smtp.sendgrid.net`, `port=587`, `username=apikey`, `password=<API_KEY>`
- AWS SES: `host=email-smtp.ap-south-1.amazonaws.com`, `port=587` + AWS credentials
`EmailService.java` code doesn't change at all — Spring Mail abstracts the transport.

**Why Thymeleaf and not FreeMarker or Mustache?**
Thymeleaf is the Spring Boot default — auto-configured with no setup. Its `th:text` attributes are valid HTML, so templates render correctly in a browser too (useful for design review). Any template engine works; the choice rarely matters.
