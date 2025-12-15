import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import sys

try:
    cred = credentials.Certificate('service-account.json')
    firebase_admin.initialize_app(cred)
    db = firestore.client()
    print("Auth successful. Attempting to list 1 doc...")
    docs = db.collection('transactions').limit(1).stream()
    count = 0
    for doc in docs:
        print(f"Found doc: {doc.id}")
        count += 1
    print(f"List complete. Found {count} docs.")
except Exception as e:
    print(f"Error: {e}")
