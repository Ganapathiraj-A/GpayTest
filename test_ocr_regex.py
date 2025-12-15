import re

# Simulated Text from Google ML Kit for the uploaded image
raw_text = """
To S KAVITHA DO V SUBRAMANI
₹20
Pay again
Completed
14 Dec 2025, 6:20 pm
UPI transaction ID
571412713621
To: S KAVITHA DO V SUBRAMANI
PhonePe • q980356526@ybl
From: Mr Ganapathi Raj (UPI Lite)
Google Pay • ganapathiraj@okaxis
Google transaction ID
CICAgOirgMXZdA
"""

print(f"--- Simulating OCR Extraction on Sample Image ---\n")
print(f"Raw Text Content:\n{raw_text}\n")

# Regex Patterns (Matching Kotlin Implementation)
UTR_PATTERN = re.compile(r'\b\d{12}\b')
# Note: Python's regex is slightly different than Java's Pattern, but logic matches.
# Java: [₹Rs.]?\s?([\d,]+\.?\d*)
AMOUNT_PATTERN = re.compile(r'[₹Rs.]?\s?([\d,]+\.?\d*)')

lines = raw_text.strip().split('\n')
found_id = None
found_amount = None

print("--- Parsing Line by Line ---")

for line in lines:
    line = line.strip()
    if not line: continue
    
    # 1. Find Transaction ID
    if not found_id:
        match = UTR_PATTERN.search(line)
        if match:
             found_id = match.group()
             print(f"[MATCH ID] Found 12-digit ID in line '{line}': {found_id}")

    # 2. Find Amount
    if not found_amount:
        # Heuristic: Check for currency symbol
        if "₹" in line or "Rs" in line:
            match = AMOUNT_PATTERN.search(line)
            if match:
                raw_amt = match.group(1).replace(",", "")
                # Validate number
                try:
                    val = float(raw_amt)
                    found_amount = raw_amt
                    print(f"[MATCH AMOUNT] Found Amount in line '{line}': {found_amount}")
                except ValueError:
                    print(f"[SKIP AMOUNT] Failed to parse number in '{line}'")

print("\n--- Final Extraction Result ---")
print(f"Transaction ID: {found_id}")
print(f"Amount: {found_amount}")

if found_id == "571412713621" and found_amount == "20":
    print("\nSUCCESS: Matches expectations for uploaded image.")
else:
    print("\nFAILURE: Did not match expectations.")
