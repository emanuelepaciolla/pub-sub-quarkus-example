#!/usr/bin/env bash
# =============================================================================
# Genera un token JWT firmato con la chiave privata locale.
# Usa SOLO openssl e python3 standard — nessuna dipendenza extra.
#
# Utilizzo:
#   ./get-token.sh              → token ADMIN (default)
#   ./get-token.sh ADMIN
#   ./get-token.sh PUBLISHER
#   ./get-token.sh READER
#
# Il token viene stampato con l'header curl pronto all'uso.
# Scade dopo 1 ora.
# =============================================================================
set -e

ROLE="${1:-ADMIN}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
KEY="$SCRIPT_DIR/dev-private-key.pem"

if [[ ! -f "$KEY" ]]; then
  echo "ERRORE: $KEY non trovato."
  echo "Genera le chiavi con:"
  echo "  openssl genrsa -out dev-private-key.pem 2048"
  echo "  openssl rsa -in dev-private-key.pem -pubout -out src/main/resources/META-INF/resources/publicKey.pem"
  exit 1
fi

TOKEN=$(python3 - "$ROLE" "$KEY" <<'PYEOF'
import base64, json, time, subprocess, sys

role = sys.argv[1]
key_path = sys.argv[2]

header = {"alg": "RS256", "typ": "JWT"}
now = int(time.time())
payload = {
    "iss":    "https://pubsub-demo.example.com",
    "sub":    "demo-user",
    "upn":    "demo-user",
    "groups": [role],
    "iat":    now,
    "exp":    now + 3600,
}

def b64url(data):
    if isinstance(data, dict):
        data = json.dumps(data, separators=(',', ':')).encode()
    return base64.urlsafe_b64encode(data).rstrip(b'=').decode()

unsigned = f"{b64url(header)}.{b64url(payload)}"

result = subprocess.run(
    ["openssl", "dgst", "-sha256", "-sign", key_path, "-binary"],
    input=unsigned.encode(),
    capture_output=True,
)
if result.returncode != 0:
    print("Errore openssl:", result.stderr.decode(), file=sys.stderr)
    sys.exit(1)

print(f"{unsigned}.{b64url(result.stdout)}")
PYEOF
)

echo ""
echo "╔══════════════════════════════════════╗"
echo "║  Token JWT — ruolo: $ROLE"
echo "╚══════════════════════════════════════╝"
echo ""
echo "$TOKEN"
echo ""
echo "━━━ Esempi curl ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "# Lista messaggi (READER+)"
echo "curl -s -H 'Authorization: Bearer $TOKEN' http://localhost:8080/messages | jq"
echo ""
echo "# Crea messaggio (PUBLISHER+)"
echo "curl -s -X POST http://localhost:8080/messages \\"
echo "  -H 'Authorization: Bearer $TOKEN' \\"
echo "  -H 'Content-Type: application/json' \\"
echo "  -d '{\"text\": \"hello world\", \"eventType\": \"DEMO\"}' | jq"
echo ""
echo "# Messaggi DLQ (solo ADMIN)"
echo "curl -s -H 'Authorization: Bearer $TOKEN' http://localhost:8080/messages/dlq | jq"
echo ""
echo "# Health (pubblico)"
echo "curl -s http://localhost:8080/q/health | jq"
echo ""
