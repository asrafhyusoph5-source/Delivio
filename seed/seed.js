/**
 * Delivio Platform - Firestore Initial Seeder Script
 *
 * Usage:
 *   1. Download your Firebase service account key from the Firebase Console:
 *      Project Settings -> Service Accounts -> "Generate New Private Key"
 *   2. Save the file as `serviceAccountKey.json` inside the `seed/` directory (or root).
 *   3. Run:
 *      npm install firebase-admin
 *      node seed/seed.js
 */

const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

// Locate service account key
const possibleKeyPaths = [
  path.join(__dirname, 'serviceAccountKey.json'),
  path.join(__dirname, '..', 'serviceAccountKey.json'),
  process.env.GOOGLE_APPLICATION_CREDENTIALS
].filter(Boolean);

let serviceAccountPath = possibleKeyPaths.find(p => fs.existsSync(p));

if (!serviceAccountPath) {
  console.error('❌ Error: serviceAccountKey.json not found!');
  console.error('Please place your Firebase serviceAccountKey.json in the `seed/` folder.');
  process.exit(1);
}

const serviceAccount = require(serviceAccountPath);

admin.initializeApp({
  credential: admin.credential.cert(serviceAccount)
});

const db = admin.firestore();

async function seedDatabase() {
  console.log('🌱 Starting Delivio Firestore database seeding...');

  const seedDataPath = path.join(__dirname, 'seed.json');
  if (!fs.existsSync(seedDataPath)) {
    console.error('❌ Error: seed/seed.json not found!');
    process.exit(1);
  }

  const rawData = fs.readFileSync(seedDataPath, 'utf8');
  const data = JSON.parse(rawData);

  // 1. Seed Cities
  if (data.cities && data.cities.length > 0) {
    console.log(`📍 Seeding ${data.cities.length} cities...`);
    for (const city of data.cities) {
      const { id, ...cityData } = city;
      await db.collection('cities').doc(id).set({
        ...cityData,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
      console.log(`   ✓ City: ${city.name} (${id})`);
    }
  }

  // 2. Seed Delivery Rates
  if (data.deliveryRates && data.deliveryRates.length > 0) {
    console.log(`🚚 Seeding ${data.deliveryRates.length} delivery rate tables...`);
    for (const rate of data.deliveryRates) {
      const { id, ...rateData } = rate;
      await db.collection('deliveryRates').doc(id).set({
        ...rateData,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
      console.log(`   ✓ Rates for city: ${id}`);
    }
  }

  // 3. Seed Banners
  if (data.banners && data.banners.length > 0) {
    console.log(`🎨 Seeding ${data.banners.length} promotional banners...`);
    for (const banner of data.banners) {
      const { id, ...bannerData } = banner;
      await db.collection('banners').doc(id).set({
        ...bannerData,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
      console.log(`   ✓ Banner: ${banner.title} (${id})`);
    }
  }

  // 4. Seed Merchants, Subcollection Categories, and Items
  if (data.merchants && data.merchants.length > 0) {
    console.log(`🏪 Seeding ${data.merchants.length} merchants and their menus...`);
    for (const merchant of data.merchants) {
      const { id, categories, items, ...merchantData } = merchant;

      await db.collection('merchants').doc(id).set({
        ...merchantData,
        createdAt: admin.firestore.FieldValue.serverTimestamp()
      }, { merge: true });
      console.log(`   ✓ Merchant: ${merchant.name} (${id})`);

      // Subcollection categories
      if (categories && categories.length > 0) {
        for (const cat of categories) {
          const { id: catId, ...catData } = cat;
          await db.collection('merchants').doc(id).collection('categories').doc(catId).set(catData, { merge: true });
        }
        console.log(`     - Seeded ${categories.length} categories`);
      }

      // Subcollection items
      if (items && items.length > 0) {
        for (const item of items) {
          const { id: itemId, ...itemData } = item;
          await db.collection('merchants').doc(id).collection('items').doc(itemId).set(itemData, { merge: true });
        }
        console.log(`     - Seeded ${items.length} items`);
      }
    }
  }

  console.log('✅ Seeding completed successfully!');
}

seedDatabase().catch((error) => {
  console.error('❌ Seeding failed with error:', error);
  process.exit(1);
});
