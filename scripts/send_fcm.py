#!/usr/bin/env python3
"""
Send an FCM HTTP v1 data message `{ "type": "REQUEST_LOCATION" }` to a device token.

Usage:
  python3 scripts/send_fcm.py --key path/to/service-account.json \
      --project YOUR_PROJECT_ID \
      --token DEVICE_FCM_TOKEN \
      [--server-key-only false]

Requires: google-auth (pip install google-auth)
"""

import argparse
import json
import sys
from typing import Any, Dict

import requests
from google.auth.transport.requests import Request
from google.oauth2 import service_account


def get_access_token(key_file: str) -> str:
    scopes = ["https://www.googleapis.com/auth/firebase.messaging"]
    creds = service_account.Credentials.from_service_account_file(key_file, scopes=scopes)
    creds.refresh(Request())
    return creds.token


def send_message(project_id: str, token: str, access_token: str) -> requests.Response:
    url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
    body: Dict[str, Any] = {
        "message": {
            "token": token,
            "data": {"type": "REQUEST_LOCATION"},
        }
    }
    headers = {
        "Authorization": f"Bearer {access_token}",
        "Content-Type": "application/json; UTF-8",
    }
    return requests.post(url, headers=headers, data=json.dumps(body))


def main() -> int:
    ap = argparse.ArgumentParser(description="Send an FCM REQUEST_LOCATION data message")
    ap.add_argument("--key", required=True, help="Path to service-account.json from Firebase")
    ap.add_argument("--project", required=True, help="Firebase project ID")
    ap.add_argument("--token", required=True, help="FCM registration token for the device")
    args = ap.parse_args()

    try:
        access_token = get_access_token(args.key)
    except Exception as ex:
        print(f"Failed to get access token: {ex}", file=sys.stderr)
        return 1

    resp = send_message(args.project, args.token, access_token)
    print(f"Status: {resp.status_code}")
    try:
        print(resp.json())
    except Exception:
        print(resp.text)
    return 0 if resp.ok else 1


if __name__ == "__main__":
    raise SystemExit(main())
