// Icons extracted without redrawing from the original NaviGlass interface.
export function icon(element, name, filled = false) {
  const image = document.createElement("img");
  image.src =
    "icons/" + (name === "heart" && !filled ? "heart-outline" : name) + ".svg";
  image.alt = "";
  image.setAttribute("aria-hidden", "true");
  image.width = 22;
  image.height = 22;
  element.replaceChildren(image);
}
