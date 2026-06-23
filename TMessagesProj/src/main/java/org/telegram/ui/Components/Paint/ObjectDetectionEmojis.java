package org.Tajgram.ui.Components.Paint;
public class ObjectDetectionEmojis {
    private static String[] labelEmojis;
    public static String labelToEmoji(int labelId) {
        if (labelEmojis == null) {
            labelEmojis = new String[] {
                    "👥", // Team
                    "🔥", // Bonfire
                    "📚", // Comics
                    "🏔", // Himalayan
                    "🧊", // Iceberg
                    "🍱", // Bento
                    null,
                    "🚰", // Sink
                    "🧸", // Toy
                    "🗿", // Statue
                    "🍔", // Cheeseburger
                    "🚜", // Tractor
                    "🛷", // Sled
                    "🐠", // Aquarium
                    "🎪", // Circus
                    null,
                    "🪑", // Sitting
                    "🧔", // Beard
                    "🌉", // Bridge
                    "🩰", // Tights
                    "🐦", // Bird
                    "🚣", // Rafting
                    "🏞", // Park
                    null,
                    "🏭", // Factory
                    "🎓", // Graduation
                    "🍶", // Porcelain
                    "🌿", // Twig
                    "🌸", // Petal
                    "🛋", // Cushion
                    "😎", // Sunglasses
                    "🏗", // Infrastructure
                    "🎡", // Ferris wheel
                    "🐠", // Pomacentridae
                    "🤿", // Wetsuit
                    "🐶", // Shetland Sheepdog
                    "⛵", // Brig
                    "🎨", // Watercolor Paint
                    "🏆", // Competition
                    "🧗", // Cliff
                    "🏸", // Badminton
                    "🦁", // Safari
                    "🚲", // Bicycle
                    "🏟", // Stadium
                    null,
                    "⛵", // Boat
                    "🙂", // Smile
                    "🏄", // Surfboard
                    "🍟", // Fast Food
                    "🌇", // Sunset
                    "🌭", // Hot Dog
                    "🩳", // Shorts
                    "🚌", // Bus
                    "🐂", // Bullfighting
                    "🌌", // Sky
                    "🐹", // Gerbil
                    "🪨", // Rock
                    "👥", // Interaction
                    "👗", // Dress
                    "👣", // Toe
                    null,
                    "🐻", // Bear
                    "🍽", // Eating
                    "🗼", // Tower
                    "🧱", // Brick
                    "🗑", // Junk
                    "👤", // Person
                    "🏄", // Windsurfing
                    "👙", // Swimwear
                    "🎢", // Roller
                    "🏕", // Camping
                    "🎠", // Playground
                    "🚽", // Bathroom
                    "😆", // Laugh
                    "🎈", // Balloon
                    "🎤", // Concert
                    "👗", // Prom
                    "🚧", // Construction
                    "📦", // Product
                    "🐠", // Reef
                    "🧺", // Picnic
                    "🌼", // Wreath
                    "🛒", // Wheelbarrow
                    "🥊", // Boxer
                    "💍", // Necklace
                    "💎", // Bracelet
                    "🎰", // Casino
                    "🚗", // Windshield
                    "🪜", // Stairs
                    "💻", // Computer
                    "🍳", // Cookware and Bakeware
                    "📽️", // Monochrome
                    "🪑", // Chair
                    "🖼", // Poster
                    "🍷", // Bar
                    "🚢", // Shipwreck
                    "🛳", // Pier
                    "👥", // Community
                    "🧗", // Caving
                    "🕳", // Cave
                    "👔", // Tie
                    "🛠", // Cabinetry
                    "🌊", // Underwater
                    "🤡", // Clown
                    "🎉", // Nightclub
                    "🚴", // Cycling
                    "☄️", // Comet
                    "🎓", // Mortarboard
                    "🏟", // Track
                    "🎄", // Christmas
                    "⛪", // Church
                    "🕰", // Clock
                    "👨", // Dude
                    "🐄", // Cattle
                    "🌴", // Jungle
                    "🖥", // Desk
                    "🥌", // Curling
                    "🍲", // Cuisine
                    "🐱", // Cat
                    "🧃", // Juice
                    "🍚", // Couscous
                    null, // "📸", // Screenshot
                    "👥", // Crew
                    "🏙", // Skyline
                    null,
                    "🧸", // Stuffed Toy
                    "🍪", // Cookie
                    "🟩", // Tile
                    "🕎", // Hanukkah
                    "🧶", // Crochet
                    "🛹", // Skateboarder
                    "✂️", // Clipper
                    "💅", // Nail
                    "🥤", // Cola
                    "🍴", // Cutlery
                    "📜", // Menu
                    null,
                    "👘", // Sari
                    "🧸", // Plush
                    "📱", // Pocket
                    "🚦", // Neon
                    "❄️", // Icicle
                    "🇵🇷", // Pasteles
                    "⛓", // Chain
                    "💃", // Dance
                    "🏜", // Dune
                    "🎅", // Santa Claus
                    "🦃", // Thanksgiving
                    "🤵", // Tuxedo
                    "👄", // Mouth
                    "🏜", // Desert
                    "🦕", // Dinosaur
                    "👳‍♂️", // Mufti
                    "🔥", // Fire
                    "🛏", // Bedroom
                    "🥽", // Goggles
                    "🐉", // Dragon
                    "🛋", // Couch
                    "🛷", // Sledding
                    "🧢", // Cap
                    "📋", // Whiteboard
                    "🎩", // Hat
                    "🍨", // Gelato
                    "🐎", // Cavalier
                    "🧶", // Beanie
                    "👕", // Jersey
                    "🧣", // Scarf
                    "🏖", // Vacation
                    "⚽", // Pitch
                    "🖤", // Blackboard
                    "🎧", // Deejay
                    "🏛", // Monument
                    "🚘", // Bumper
                    "🛹", // Longboard
                    "🦢", // Waterfowl
                    "🍖", // Flesh
                    "🥅", // Net
                    "🧁", // Icing
                    "🐕", // Dalmatian
                    "🚤", // Speedboat
                    "🌳", // Trunk
                    "☕", // Coffee
                    "⚽", // Soccer
                    "🧸", // Ragdoll
                    "🍲", // Food
                    "🧍", // Standing
                    "📖", // Fiction
                    "🍉", // Fruit
                    "🍜", // Pho
                    "✨", // Sparkler
                    "💼", // Presentation
                    "🌳", // Swing
                    "🐕", // Cairn Terrier
                    "🌲", // Forest
                    "🚩", // Flag
                    "⛵", // Frigate
                    "🦶", // Foot
                    "🧥", // Jacket
                    null,
                    "🛏", // Pillow
                    null,
                    "🛁", // Bathing
                    "🗻", // Glacier
                    "🤸‍♀️", // Gymnastics
                    "👂", // Ear
                    "🌸", // Flora
                    "🐚", // Shell
                    "👵", // Grandparent
                    "🏛", // Ruins
                    "👁️", // Eyelash
                    "🛏", // Bunk Bed
                    "⚖️", // Balance
                    "🎒", // Backpacking
                    "🐎", // Horse
                    "✨", // Glitter
                    "🛸", // Saucer
                    "💇", // Hair
                    "🧸", // Miniature
                    "👥", // Crowd
                    "🪟", // Curtain
                    "🌟", // Icon
                    "🐱", // Pixie-bob
                    "🐄", // Herd
                    "🐞", // Insect
                    "❄️", // Ice
                    "💍", // Bangle
                    "🚪", // Flap
                    "💎", // Jewellery
                    "🧶", // Knitting
                    "🏺", // Centrepiece
                    "🧥", // Outerwear
                    "❤️", // Love
                    "💪", // Muscle
                    "🏍", // Motorcycle
                    "💰", // Money
                    "🕌", // Mosque
                    "🍽", // Tableware
                    "💃", // Ballroom
                    "🛶", // Kayak
                    "🏖", // Leisure
                    "🧾", // Receipt
                    "🏞", // Lake
                    "🚨", // Lighthouse
                    "🐴", // Bridle
                    "🧥", // Leather
                    "📯", // Horn
                    "⌚", // Strap
                    "🧱", // Lego
                    "🤿", // Scuba Diving
                    "👖", // Leggings
                    "🏊", // Pool
                    "🎸", // Musical Instrument
                    "🎭", // Musical
                    "🤘", // Metal
                    "🌕", // Moon
                    "🧥", // Blazer
                    "💍", // Marriage
                    "📱", // Mobile Phone
                    "🪖", // Militia
                    "🍽", // Tablecloth
                    "🎉", // Party
                    "🌌", // Nebula
                    "📰", // News
                    "🗞", // Newspaper
                    null,
                    "🎹", // Piano
                    "🪴", // Plant
                    "🛂", // Passport
                    "🐧", // Penguin
                    "🐕", // Shikoku
                    "🏰", // Palace
                    "🏵", // Doily
                    "🏇", // Polo
                    "📝", // Paper
                    "🎶", // Pop Music
                    "⛵", // Skiff
                    "🍕", // Pizza
                    "🐾", // Pet
                    "🧵", // Quilting
                    "🐦", // Cage
                    "🛹", // Skateboard
                    "🏄", // Surfing
                    "🏉", // Rugby
                    "💄", // Lipstick
                    "🏞", // River
                    "🏁", // Race
                    "🚣", // Rowing
                    "🛣", // Road
                    "🏃", // Running
                    "🛋", // Room
                    "🏠", // Roof
                    "⭐", // Star
                    "🏅", // Sports
                    "👟", // Shoe
                    "🚤", // Tubing
                    "🪐", // Space
                    "😴", // Sleep
                    "🤲", // Skin
                    "🏊", // Swimming
                    "🏫", // School
                    "🍣", // Sushi
                    "🛋", // Loveseat
                    "🦸", // Superman
                    "😎", // Cool
                    "⛷", // Skiing
                    "🚢", // Submarine
                    "🎵", // Song
                    "📚", // Class
                    "🏙", // Skyscraper
                    "🌋", // Volcano
                    "📺", // Television
                    "🐎", // Rein
                    "💉", // Tattoo
                    "🚆", // Train
                    "🚪", // Handrail
                    "🥤", // Cup
                    "🚗", // Vehicle
                    "👜", // Handbag
                    "💡", // Lampshade
                    "🎫", // Event
                    "🍷", // Wine
                    "🍗", // Wing
                    "🎡", // Wheel
                    "🏄", // Wakeboarding
                    "💻", // Web Page
                    null,
                    null,
                    "🏡", // Ranch
                    "🎣", // Fishing
                    "❤️", // Heart
                    "🌱", // Cotton
                    "☕", // Cappuccino
                    "🍞", // Bread
                    "🏖", // Sand
                    null,
                    "🏛", // Museum
                    "🚁", // Helicopter
                    "⛰", // Mountain
                    "🦆", // Duck
                    "🌱", // Soil
                    "🐢", // Turtle
                    "🐊", // Crocodile
                    "🎶", // Musician
                    "👟", // Sneakers
                    "🧶", // Wool
                    "💍", // Ring
                    "🎤", // Singer
                    "🎡", // Carnival
                    "🏂", // Snowboarding
                    "🚤", // Waterskiing
                    "🧱", // Wall
                    "🚀", // Rocket
                    "🏠", // Countertop
                    "🏖", // Beach
                    "🌈", // Rainbow
                    "🌿", // Branch
                    "👨", // Moustache
                    "🌷", // Garden
                    "👗", // Gown
                    "🏞", // Field
                    "🐶", // Dog
                    "🦸", // Superhero
                    "🌸", // Flower
                    "🍽", // Placemat
                    "🔊", // Subwoofer
                    "⛪", // Cathedral
                    "🏢", // Building
                    "✈️", // Airplane
                    "🐾", // Fur
                    "🐂", // Bull
                    "🪑", // Bench
                    "🛕", // Temple
                    "🦋", // Butterfly
                    "👠", // Model
                    "🏃", // Marathon
                    "🪡", // Needlework
                    "🍳", // Kitchen
                    "🏰", // Castle
                    "🌌", // Aurora
                    "🐛", // Larva
                    "🏎", // Racing
                    null,
                    "✈️", // Airliner
                    "🚣", // Dam
                    "🧵", // Textile
                    "🤵", // Groom
                    "🎢", // Fun
                    "🍲", // Steaming
                    "🥦", // Vegetable
                    "🚲", // Unicycle
                    "👖", // Jeans
                    "🪴", // Flowerpot
                    "🗄", // Drawer
                    "🎂", // Cake
                    "💺", // Armrest
                    "✈️", // Aviation
                    null,
                    "🌫", // Fog
                    "🎆", // Fireworks
                    "🚜", // Farm
                    "🦭", // Seal
                    "📚", // Shelf
                    "💇", // Bangs
                    "⚡", // Lightning
                    "🚐", // Van
                    "🐱", // Sphynx
                    "🚗", // Tire
                    "👖", // Denim
                    "🌾", // Prairie
                    "🤿", // Snorkeling
                    "☔", // Umbrella
                    "🛣", // Asphalt
                    "⛵", // Sailboat
                    "🐶", // Basset Hound
                    "🔳", // Pattern
                    "🍽", // Supper
                    "👰", // Veil
                    "💧", // Waterfall
                    null,
                    "🍴", // Lunch
                    "🚙", // Odometer
                    "👶", // Baby
                    "👓", // Glasses
                    "🚗", // Car
                    "✈️", // Aircraft
                    "✋", // Hand
                    "🐎", // Rodeo
                    "🏞", // Canyon
                    "🍽", // Meal
                    "⚾", // Softball
                    "🍷", // Alcohol
                    "👰", // Bride
                    "🌿", // Swamp
                    "🥧", // Pie
                    "🎒", // Bag
                    "🃏", // Joker
                    "🦹", // Supervillain
                    "🪖", // Army
                    "🛶", // Canoe
                    "🤳", // Selfie
                    "🛺", // Rickshaw
                    "🏚", // Barn
                    "🏹", // Archery
                    "🚀", // Aerospace Engineering
                    null,
                    "⛈", // Storm
                    "⛑", // Helmet
            };
        }
        if (labelId < 0 || labelId >= labelEmojis.length) return null;
        return labelEmojis[labelId];
    }
}