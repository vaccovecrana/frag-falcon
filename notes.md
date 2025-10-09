# Step 1: Get a temporary auth token (scope limited to pull for this repo)
TOKEN=$(curl -s "https://auth.docker.io/token?service=registry.docker.io&scope=repository:library/redis:pull" | jq -r '.token')

# Step 2: Fetch the manifest and get the digest from headers
curl -s -D - -H "Authorization: Bearer $TOKEN" \
-H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
https://registry-1.docker.io/v2/library/redis/manifests/8.0-alpine | grep -i Docker-Content-Digest

# Assume $PAT is your GitHub token
# For public images, auth might not be needed, but it's recommended
curl -s -H "Authorization: Bearer $PAT" \
-H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
https://ghcr.io/v2/<owner>/<image>/manifests/8.0-alpine -v 2>&1 | grep -i Docker-Content-Digest

# If auth needed, get a bearer token from Quay UI or API
curl -s -H "Authorization: Bearer $QUAY_TOKEN" \
-H "Accept: application/vnd.docker.distribution.manifest.v2+json" \
https://quay.io/v2/<namespace>/<repository>/manifests/8.0-alpine -v 2>&1 | grep -i Docker-Content-Digest