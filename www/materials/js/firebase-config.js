// ==================== إعدادات Firebase ====================

const firebaseConfig = {
    apiKey: "AIzaSyDQbf5LJRCquRsheFYqvEQBQbI_EoXNOFw",
    authDomain: "abo-slman.firebaseapp.com",
    projectId: "abo-slman",
    storageBucket: "abo-slman.firebasestorage.app",
    messagingSenderId: "874996942668",
    appId: "1:874996942668:web:f31da5ca778fb92845f1e9"
};

if (!firebase.apps.length) {
    firebase.initializeApp(firebaseConfig);
}

const db = firebase.firestore();
const materialsCollection = db.collection("spices_final_v12");
const pricesCollection = db.collection("material_prices");
