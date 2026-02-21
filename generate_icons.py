import os
from PIL import Image

# Define the source image and the target directory for Android resources
SOURCE_IMAGE = "app_icon.png"
RES_DIR = "TMessagesProj/src/main/res"

# Android icon sizes for launchers
ICON_SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192
}

def generate_icons():
    if not os.path.exists(SOURCE_IMAGE):
        print(f"Error: {SOURCE_IMAGE} not found!")
        return

    try:
        img = Image.open(SOURCE_IMAGE)
    except Exception as e:
        print(f"Error opening image: {e}")
        return

    # Ensure it's in a mode that supports transparency if it's a PNG
    img = img.convert("RGBA")

    for folder, size in ICON_SIZES.items():
        target_dir = os.path.join(RES_DIR, folder)
        
        # Create directory if it doesn't exist (though it should in Telegram source)
        if not os.path.exists(target_dir):
            os.makedirs(target_dir)

        # Resize image using high-quality downsampling
        resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save both normal and round versions (for modern Android support)
        launcher_path = os.path.join(target_dir, "ic_launcher.png")
        round_launcher_path = os.path.join(target_dir, "ic_launcher_round.png")
        
        resized_img.save(launcher_path, format="PNG")
        resized_img.save(round_launcher_path, format="PNG")
        
        print(f"Generated {size}x{size} icons in {folder}")

if __name__ == "__main__":
    generate_icons()
    print("Done generating SpaceGram icons.")
