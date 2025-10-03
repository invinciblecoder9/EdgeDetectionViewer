import FrameViewer from './FrameViewer.js';

// Sample base64 image for demonstration
const SAMPLE_EDGE_FRAME = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';

document.addEventListener('DOMContentLoaded', () => {
    const viewer = new FrameViewer('frameCanvas', 'statsDisplay');

    // Button handlers
    const loadSampleBtn = document.getElementById('loadSample') as HTMLButtonElement;
    const clearBtn = document.getElementById('clear') as HTMLButtonElement;
    const fileInput = document.getElementById('fileInput') as HTMLInputElement;
    const loadFileBtn = document.getElementById('loadFile') as HTMLButtonElement;

    // Load sample frame
    loadSampleBtn?.addEventListener('click', () => {
        viewer.loadFrameFromBase64(SAMPLE_EDGE_FRAME)
            .then(() => {
                viewer.updateFrameStats(15.2, 32.5);
                console.log('Sample frame loaded');
            })
            .catch(err => console.error('Error loading sample:', err));
    });

    // Clear canvas
    clearBtn?.addEventListener('click', () => {
        viewer.clear();
    });

    // Load from file
    loadFileBtn?.addEventListener('click', () => {
        fileInput?.click();
    });

    fileInput?.addEventListener('change', (event) => {
        const file = (event.target as HTMLInputElement).files?.[0];
        if (file) {
            const reader = new FileReader();
            reader.onload = (e) => {
                const base64 = e.target?.result as string;
                viewer.loadFrameFromBase64(base64)
                    .then(() => {
                        viewer.updateFrameStats(15.0, 30.0);
                        console.log('File loaded successfully');
                    })
                    .catch(err => console.error('Error loading file:', err));
            };
            reader.readAsDataURL(file);
        }
    });

    console.log('Edge Detection Web Viewer initialized');
});
