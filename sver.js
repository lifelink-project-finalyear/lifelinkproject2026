const express = require("express");
const http = require("http");
const cors = require("cors");
const { Server } = require("socket.io");
const Database = require("better-sqlite3");


const app = express();
app.use(cors());
app.use(express.json());
app.use(express.static("."));

const server = http.createServer(app);
const io = new Server(server, {
  cors: { origin: "*" }
});

const latestByCase = new Map();
const db = new Database("livelink.db");

db.exec(`
  CREATE TABLE IF NOT EXISTS location_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    caseId TEXT NOT NULL,
    patientId TEXT,
    diseaseType TEXT,
    source TEXT,
    lat REAL NOT NULL,
    lng REAL NOT NULL,
    timestamp INTEGER NOT NULL
  );
`);

const tableInfo = db.prepare("PRAGMA table_info(location_history)").all();
const hasSource = tableInfo.some((col) => col.name === "source");
if (!hasSource) {
  db.exec("ALTER TABLE location_history ADD COLUMN source TEXT DEFAULT 'user'");
}

const insertHistory = db.prepare(`
  INSERT INTO location_history (caseId, patientId, diseaseType, source, lat, lng, timestamp)
  VALUES (@caseId, @patientId, @diseaseType, @source, @lat, @lng, @timestamp)
`);



app.post("/location/update", (req, res) => {
  const { caseId, patientId, diseaseType, source, lat, lng, timestamp } = req.body || {};

  if (!caseId || typeof lat !== "number" || typeof lng !== "number") {
    return res.status(400).json({
      ok: false,
      message: "caseId, lat(number), lng(number) are required"
    });
  }

  const payload = {
    caseId,
    patientId: patientId || "NA",
    diseaseType: diseaseType || "NA",
    source: source === "ambulance" ? "ambulance" : "user",
    lat,
    lng,
    timestamp: timestamp || Date.now()
  };

  const key = `${caseId}:${payload.source}`;
  latestByCase.set(key, payload);
  insertHistory.run(payload);
  io.emit("location_update", payload);

  res.json({ ok: true });
});

app.get("/location/latest/:caseId", (req, res) => {
  const source = req.query.source === "ambulance" ? "ambulance" : "user";
  const data = latestByCase.get(`${req.params.caseId}:${source}`) || null;
  res.json({ ok: true, data });
});

app.get("/location/history/:caseId", (req, res) => {
  const limit = Math.min(Number(req.query.limit) || 200, 1000);
  const source = req.query.source;
  let rows = [];
  if (source === "ambulance" || source === "user") {
    rows = db
      .prepare("SELECT * FROM location_history WHERE caseId = ? AND source = ? ORDER BY timestamp DESC LIMIT ?")
      .all(req.params.caseId, source, limit);
  } else {
    rows = db
      .prepare("SELECT * FROM location_history WHERE caseId = ? ORDER BY timestamp DESC LIMIT ?")
      .all(req.params.caseId, limit);
  }
  res.json({ ok: true, data: rows });
});

io.on("connection", (socket) => {
  const allCases = Array.from(latestByCase.values());
  socket.emit("bootstrap_cases", allCases);
});

const PORT = 3000;
server.listen(PORT, () => {
  console.log(`LifeLink tracking server running on http://localhost:${PORT}`);
});
