import "../sakai-reorderer.js";
import { fixture, html } from "@open-wc/testing";

describe("sakai-reorderer tests", () => {

  it ("renders correctly", async () => {

    let el = await fixture(html`
      <sakai-reorderer>
        <div>
          <div draggabe="true"><span class="drag-handle"></span>Item 1</div>
          <div draggabe="true"><span class="drag-handle"></span>Item 2</div>
        </div>
      </sakai-reorderer>
    `);
  });
});
