import React from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./styles.css";

document.addEventListener(
  "submit",
  (event) => {
    event.preventDefault();
  },
  true
);

createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);
