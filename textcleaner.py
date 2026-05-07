import os
import re
from pathlib import Path
from cleantext import clean

def custom_clean(text):
    """
    Applies custom cleaning rules before passing the text to clean-text.
    """
    # 1. Remove specific "@" string pattern
    text = text.replace("@ @ @ @ @ @ @ @ @ @", "")
    
    # 1b. Remove "@!" from all words with this pattern
    text = text.replace("@!", "")
    
    # 2. Remove HTML tags using a regular expression
    # This matches anything starting with '<' and ending with '>', stripping it out.
    text = re.sub(r'<[^>]+>', '', text)
    
    # 3. Apply the standard clean-text library processing
    cleaned_text = clean(text,
        fix_unicode=True,
        to_ascii=True,
        lower=True,
        no_line_breaks=False,
        no_urls=True,
        no_emails=True,
        no_phone_numbers=False,
        no_numbers=False,
        no_digits=False,
        no_currency_symbols=False,
        no_punct=False,
        replace_with_url="<URL>",
        replace_with_email="<EMAIL>"
    )
    
    return cleaned_text

def main():
    # Define directories
    input_dir = "DataSources/CocaText"
    output_dir = "CleanedTexts"
    
    # Create directories if they don't exist so the script doesn't crash
    Path(input_dir).mkdir(parents=True, exist_ok=True)
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    
    # Grab all .txt files from the input directory
    files = [f for f in os.listdir(input_dir) if f.endswith(".txt")]
    
    # Sort files alphabetically for a predictable list
    files.sort()
    
    if not files:
        print(f"No .txt files found in '{input_dir}'. Please add some and try again.")
        return

    # Present the list of files to the user
    print(f"\nFound {len(files)} files in '{input_dir}':")
    for i, filename in enumerate(files):
        print(f"{i + 1}. {filename}")
        
    # Get user selection with basic error handling
    while True:
        try:
            choice = int(input(f"\nEnter the number of the file you'd like to start with (1-{len(files)}): "))
            if 1 <= choice <= len(files):
                start_index = choice - 1
                break
            else:
                print("Invalid choice. Please pick a number from the list.")
        except ValueError:
            print("Invalid input. Please enter an integer.")
            
    # Slice the list to start from the user's chosen file
    files_to_process = files[start_index:]
    
    print(f"\n--- Starting cleaning process from '{files[start_index]}' ---")
    
    for filename in files_to_process:
        input_path = os.path.join(input_dir, filename)
        output_path = os.path.join(output_dir, filename)
        
        # Read file
        try:
            with open(input_path, 'r', encoding='utf-8') as file:
                raw_text = file.read()
        except Exception as e:
            print(f"Error reading {filename}: {e}")
            continue
            
        # Clean text
        cleaned = custom_clean(raw_text)
        
        # Save file
        with open(output_path, 'w', encoding='utf-8') as file:
            file.write(cleaned)
            
        print(f"Successfully cleaned: {filename}")

    print("\nAll selected files have been processed and saved to the output directory!")

if __name__ == "__main__":
    main()