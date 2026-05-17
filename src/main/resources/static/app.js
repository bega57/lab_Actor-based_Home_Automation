async function refreshStatus() {
    try {
        const response = await fetch("/status");
        const data = await response.json();

        document.getElementById("temperature").innerText =
            data.temperature.toFixed(1) + " °C";

        document.getElementById("weather").innerText = data.weather;

        document.getElementById("acStatus").innerText =
            data.airConditionOn ? "ON" : "OFF";

        document.getElementById("blindsStatus").innerText =
            data.blindsClosed ? "CLOSED" : "OPEN";

        // TV screen – show movie title when playing
        const tv = document.getElementById("tvScreen");
        if (data.moviePlaying) {
            tv.innerText = data.currentTitle ? data.currentTitle : "PLAYING";
            tv.classList.add("tv-on");
        } else {
            tv.innerText = "OFF";
            tv.classList.remove("tv-on");
        }

        // AC indicator
        const acLight = document.getElementById("acLight");
        if (data.airConditionOn) {
            acLight.classList.add("ac-active");
        } else {
            acLight.classList.remove("ac-active");
        }

        // Blinds
        const blinds = document.getElementById("blinds");
        if (data.blindsClosed) {
            blinds.classList.add("closed");
        } else {
            blinds.classList.remove("closed");
        }

    } catch (error) {
        console.log("Status refresh error:", error);
    }
}

async function refreshFridge() {
    try {
        const response = await fetch("/fridge");
        const data = await response.json();

        const container = document.getElementById("fridgeContents");
        container.innerHTML = "";

        for (const key in data) {
            const item = document.createElement("div");
            item.className = "fridge-item";
            item.innerHTML = `<span>${key}</span><strong>${data[key]}</strong>`;
            container.appendChild(item);
        }
    } catch (error) {
        console.log("Fridge refresh error:", error);
    }
}

async function refreshHistory() {
    try {
        const response = await fetch("/orderHistory");
        const data = await response.json();

        const container = document.getElementById("orderHistory");
        container.innerHTML = "";

        data.forEach(order => {
            const item = document.createElement("div");
            item.className = "list-item";
            item.innerHTML = `<span>${order.product}</span><strong>${order.amount}x</strong>`;
            container.appendChild(item);
        });
    } catch (error) {
        console.log("History refresh error:", error);
    }
}

/**
 * Reads the movie title from the input field and sends it as a URL path segment.
 * Falls back to "Unknown Movie" if the field is empty.
 */
async function playMovie() {
    const titleInput = document.getElementById("movieTitle");
    const raw = (titleInput && titleInput.value.trim()) ? titleInput.value.trim() : "Unknown Movie";
    const encoded = encodeURIComponent(raw);
    await fetch(`/playMovie/${encoded}`);
}

async function stopMovie() {
    await fetch("/stopMovie");
}

async function orderProduct() {
    const name   = document.getElementById("productName").value;
    const amount = document.getElementById("productAmount").value;
    if (!name || !amount) return;
    await fetch(`/order/${name}/${amount}`);
    refreshFridge();
    refreshHistory();
}

async function consumeProduct() {
    const name   = document.getElementById("consumeName").value;
    const amount = document.getElementById("consumeAmount").value;
    if (!name || !amount) return;
    await fetch(`/consume/${name}/${amount}`);
    refreshFridge();
}

async function setMode(mode) {
    await fetch(`/mode/${mode}`);
}

async function simulation(active) {
    await fetch(active ? "/simulation/on" : "/simulation/off");
}

async function setManualTemperature() {
    const val = document.getElementById("manualTemp").value;
    if (val === "" || val === null) return;
    await fetch(`/setTemperature/${val}`);
}

async function setManualWeather() {
    const val = document.getElementById("manualWeather").value;
    if (!val) return;
    await fetch(`/setWeather/${val}`);
}

async function clearHistory() {
    await fetch("/clearHistory");
    refreshHistory();
}

setInterval(() => {
    refreshStatus();
    refreshFridge();
    refreshHistory();
}, 1000);

refreshStatus();
refreshFridge();
refreshHistory();
