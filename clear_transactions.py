import firebase_admin
from firebase_admin import credentials
from firebase_admin import firestore
import sys

def delete_collection(coll_ref, batch_size):
    docs = coll_ref.limit(batch_size).stream()
    deleted = 0

    for doc in docs:
        print(f'Deleting doc {doc.id} => {doc.to_dict()}')
        doc.reference.delete()
        deleted = deleted + 1

    if deleted >= batch_size:
        return delete_collection(coll_ref, batch_size)

def main():
    # Fetch the service account key JSON file contents
    cred = credentials.Certificate('service-account.json')

    # Initialize the app with a service account, granting admin privileges
    if not firebase_admin._apps:
        firebase_admin.initialize_app(cred)

    db = firestore.client()

    print("Starting deletion of 'transactions' collection...")
    
    # Reference to the transactions collection
    transactions_ref = db.collection('transactions')
    
    # Delete docs in batches
    delete_collection(transactions_ref, 10)
    
    print("All transactions deleted successfully.")

if __name__ == "__main__":
    main()
