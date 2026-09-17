"""
DhilipHome Server - Security Utility
Provides path traversal prevention, secure password hashing,
cryptographic token generation, and role-based authentication decorators.
"""

import hmac
import hashlib
import time
import base64
import json
from pathlib import Path
from functools import wraps
from typing import Optional, Tuple, Dict, Any
from flask import request, jsonify
from werkzeug.security import generate_password_hash, check_password_hash
from app.utils.config import Config


def is_safe_path(base_dir: Path, target_path: Path) -> bool:
    """
    Validates that target_path resides completely within base_dir.
    Prevents directory traversal attacks (../, absolute overrides, symlink breakouts).
    """
    try:
        resolved_base = base_dir.resolve()
        resolved_target = target_path.resolve()
        # Ensure target begins with base path
        return resolved_base == resolved_target or resolved_base in resolved_target.parents
    except Exception:
        return False


def hash_password(password: str) -> str:
    """Hash password using PBKDF2/SHA256 via Werkzeug."""
    return generate_password_hash(password, method="pbkdf2:sha256:600000")


def verify_password(password: str, hashed_password: str) -> bool:
    """Verify raw password against hashed password."""
    try:
        return check_password_hash(hashed_password, password)
    except Exception:
        return False


def create_token(payload: Dict[str, Any], expiry_seconds: int = None) -> str:
    """
    Create a cryptographically signed URL-safe authentication token.
    Contains timestamp, payload, and HMAC-SHA256 signature.
    """
    if expiry_seconds is None:
        expiry_seconds = Config.AUTH_TOKEN_EXPIRY

    header = {"alg": "HS256", "typ": "DHTOKEN"}
    claims = {
        **payload,
        "iat": int(time.time()),
        "exp": int(time.time()) + expiry_seconds,
    }

    raw_header = base64.urlsafe_b64encode(json.dumps(header, separators=(",", ":")).encode()).decode().rstrip("=")
    raw_claims = base64.urlsafe_b64encode(json.dumps(claims, separators=(",", ":")).encode()).decode().rstrip("=")
    signing_input = f"{raw_header}.{raw_claims}"

    signature = hmac.new(
        Config.SECRET_KEY.encode(),
        signing_input.encode(),
        hashlib.sha256,
    ).digest()
    raw_sig = base64.urlsafe_b64encode(signature).decode().rstrip("=")

    return f"{signing_input}.{raw_sig}"


def verify_token(token: str) -> Tuple[bool, Optional[Dict[str, Any]], Optional[str]]:
    """
    Verifies token validity, expiration, and signature.
    Returns: (is_valid, claims_dict_or_None, error_message_or_None)
    """
    if not token or not isinstance(token, str):
        return False, None, "Token missing"

    parts = token.strip().split(".")
    if len(parts) != 3:
        return False, None, "Invalid token format"

    raw_header, raw_claims, raw_sig = parts
    signing_input = f"{raw_header}.{raw_claims}"

    # Verify signature
    expected_sig = hmac.new(
        Config.SECRET_KEY.encode(),
        signing_input.encode(),
        hashlib.sha256,
    ).digest()
    expected_raw_sig = base64.urlsafe_b64encode(expected_sig).decode().rstrip("=")

    if not hmac.compare_digest(raw_sig, expected_raw_sig):
        return False, None, "Invalid token signature"

    # Decode claims
    try:
        # Add padding back if necessary
        padded_claims = raw_claims + "=" * (-len(raw_claims) % 4)
        claims_json = base64.urlsafe_b64decode(padded_claims.encode()).decode("utf-8")
        claims = json.loads(claims_json)
    except Exception:
        return False, None, "Corrupted token payload"

    # Check expiration
    exp = claims.get("exp")
    if exp and time.time() > exp:
        return False, None, "Token has expired"

    return True, claims, None


def get_request_token() -> Optional[str]:
    """Extract auth token from Authorization Header, Bearer, Cookie, or Query Param."""
    # 1. Bearer Header
    auth_header = request.headers.get("Authorization")
    if auth_header and auth_header.startswith("Bearer "):
        return auth_header[7:].strip()

    # 2. Custom header
    if request.headers.get("X-Auth-Token"):
        return request.headers.get("X-Auth-Token").strip()

    # 3. Query string (useful for media audio/video streaming via Android MediaPlayer/ExoPlayer)
    if request.args.get("token"):
        return request.args.get("token").strip()

    # 4. Cookie
    if request.cookies.get("dhiliphome_token"):
        return request.cookies.get("dhiliphome_token").strip()

    return None


def require_auth(f):
    """
    Decorator requiring a valid authentication token.
    Attaches current_user dictionary to Flask's request context.
    """
    @wraps(f)
    def decorated(*args, **kwargs):
        token = get_request_token()
        if not token:
            return error_response("AUTH_REQUIRED", "Authentication token is required for this action", 401)

        is_valid, claims, err = verify_token(token)
        if not is_valid:
            return error_response("AUTH_INVALID", f"Authentication failed: {err}", 401)

        request.current_user = claims
        return f(*args, **kwargs)

    return decorated


def success_response(data: Any = None, message: str = None, status: int = 200):
    """Consistent JSON success response format."""
    payload = {"success": True}
    if data is not None:
        payload["data"] = data
    if message:
        payload["message"] = message
    return jsonify(payload), status


def error_response(code: str, message: str, status: int = 400):
    """Consistent JSON error response format conforming to Section 20."""
    return jsonify({
        "success": False,
        "error": {
            "code": code,
            "message": message,
        }
    }), status
