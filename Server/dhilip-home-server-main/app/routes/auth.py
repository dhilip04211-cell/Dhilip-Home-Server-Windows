"""
DhilipHome Server - Authentication Routes
Provides login, session status, and logout endpoints for administrators and clients.
"""

from flask import Blueprint, request, jsonify
from app.database.models import UserModel
from app.utils.security import (
    verify_password,
    create_token,
    get_request_token,
    verify_token,
    require_auth,
    success_response,
    error_response,
)
from app.utils.config import Config

auth_bp = Blueprint("auth", __name__)


@auth_bp.route("/api/auth/login", methods=["POST"])
def login():
    """
    Authenticate user credentials and issue signed token (Section 14).
    JSON body:
    - username: str
    - password: str
    """
    data = request.get_json(silent=True) or {}
    username = (data.get("username") or request.form.get("username", "")).strip()
    password = (data.get("password") or request.form.get("password", "")).strip()

    if not username or not password:
        return error_response("CREDENTIALS_REQUIRED", "Both username and password are required", 400)

    user = UserModel.get_by_username(username)
    if not user:
        return error_response("INVALID_CREDENTIALS", "Invalid username or password", 401)

    if not verify_password(password, user["password_hash"]):
        return error_response("INVALID_CREDENTIALS", "Invalid username or password", 401)

    # Update last login timestamp
    UserModel.update_last_login(user["id"])

    # Create token payload
    token_payload = {
        "sub": user["id"],
        "username": user["username"],
        "role": user["role"],
    }
    token = create_token(token_payload)

    response_data = {
        "token": token,
        "token_type": "Bearer",
        "expires_in": Config.AUTH_TOKEN_EXPIRY,
        "user": {
            "id": user["id"],
            "username": user["username"],
            "role": user["role"],
        },
    }

    resp, status_code = success_response(response_data, "Login successful")
    # Set cookie for browser-friendly sessions
    resp.set_cookie("dhiliphome_token", token, max_age=Config.AUTH_TOKEN_EXPIRY, httponly=True, samesite="Lax")
    return resp, status_code


@auth_bp.route("/api/auth/logout", methods=["POST"])
def logout():
    """
    Log out active session and clear cookies.
    """
    resp, status_code = success_response({"logged_out": True}, "Successfully logged out")
    resp.delete_cookie("dhiliphome_token")
    return resp, status_code


@auth_bp.route("/api/auth/status", methods=["GET"])
def auth_status():
    """
    Check current authentication status.
    """
    token = get_request_token()
    if not token:
        return jsonify({
            "authenticated": False,
            "user": None,
        }), 200

    is_valid, claims, err = verify_token(token)
    if not is_valid:
        return jsonify({
            "authenticated": False,
            "error": err,
            "user": None,
        }), 200

    return jsonify({
        "authenticated": True,
        "user": {
            "id": claims.get("sub"),
            "username": claims.get("username"),
            "role": claims.get("role"),
        },
    }), 200
