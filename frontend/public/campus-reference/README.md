# Campus reference library

This directory is the source-of-truth index for the Campus 25 3D model. The attached 242-page scanned PDF was audited on 2026-08-17 but remains outside the repository as a large source document; `manifest.json` records its filename and complete page audit until the document or compressed derivatives are copied here.

Organize future evidence under these categories:

- `building/` - building fronts, side elevations, roofs, entrances, and floor-count evidence
- `entrance/` - gates, exits, security posts, turnstiles, and access-control points
- `pathway/` - pedestrian paths, crossings, ramps, bottlenecks, and sidewalks
- `road/` - vehicle roads, parking lanes, curbs, and turning areas
- `landmark/` - statues, sculptures, signs, trees only when their positions are known, and other repeatable landmarks
- `viewpoint/` - wide campus views or sequence frames used to relate multiple areas

For each image or PDF, add an entry to `manifest.json` with:

```json
{
  "path": "building/academic-east-01.jpg",
  "category": "building",
  "areas": ["Academic Building"],
  "page": null,
  "landmarks": ["Academic Building east entrance"],
  "notes": "Facing west from the main path"
}
```

Use `page` for PDF page numbers (1-based). Inspect every PDF page, including pages that contain only a diagram, label, coordinate, or map. Do not infer a wall, tree, entrance, height, or restricted boundary from a single ambiguous image; record it as `unverified` until coordinates or corroborating evidence are supplied.

The 3D map's **Reference/debug** mode reads this manifest at runtime and shows the photo/PDF evidence attached to each area. Images are lazy-loaded only when the debug panel is open.
