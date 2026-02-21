import os
from PIL import Image  # type: ignore

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

# The names required by the Play Store version and Standalone version
ICON_NAMES = [
    "ic_launcher.png",
    "ic_launcher_round.png",
    "ic_launcher_sa.png",
    "icon_2_launcher_sa.png",
    "icon_3_launcher_sa.png",
    "icon_4_launcher_sa.png",
    "icon_5_launcher_sa.png",
    "icon_6_launcher_sa.png"
]

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
        
        # Create directory if it doesn't exist
        if not os.path.exists(target_dir):
            os.makedirs(target_dir)

        # Resize image using high-quality downsampling
        resized_img = img.resize((size, size), Image.Resampling.LANCZOS)
        
        # Save all the required icon names
        for icon_name in ICON_NAMES:
            path = os.path.join(target_dir, icon_name)
            resized_img.save(path, format="PNG")
        
        print(f"Generated {size}x{size} icons in {folder} (including Standalone aliases)")

if __name__ == "__main__":
    generate_icons()
    print("Done generating SpaceGram icons.")
