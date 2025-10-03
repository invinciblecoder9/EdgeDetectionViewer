interface FrameStats {
    fps: number;
    resolution: string;
    processingTime: number;
    timestamp: number;
}

class FrameViewer {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private statsElement: HTMLElement;
    private currentFrame: HTMLImageElement | null = null;
    private stats: FrameStats;

    constructor(canvasId: string, statsId: string) {
        this.canvas = document.getElementById(canvasId) as HTMLCanvasElement;
        this.statsElement = document.getElementById(statsId) as HTMLElement;

        if (!this.canvas) {
            throw new Error(`Canvas element with id "${canvasId}" not found`);
        }

        const context = this.canvas.getContext('2d');
        if (!context) {
            throw new Error('Failed to get 2D context');
        }
        this.ctx = context;

        this.stats = {
            fps: 0,
            resolution: '0x0',
            processingTime: 0,
            timestamp: Date.now()
        };

        this.setupCanvas();
    }

    private setupCanvas(): void {
        // Set canvas size
        this.canvas.width = 640;
        this.canvas.height = 480;

        // Set background
        this.ctx.fillStyle = '#000000';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        // Display instructions
        this.ctx.fillStyle = '#FFFFFF';
        this.ctx.font = '16px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.fillText('Waiting for frame...', this.canvas.width / 2, this.canvas.height / 2);
    }

    /**
     * Load and display a frame from a base64 string
     */
    public loadFrameFromBase64(base64Data: string): Promise<void> {
        return new Promise((resolve, reject) => {
            const img = new Image();

            img.onload = () => {
                this.currentFrame = img;
                this.renderFrame();
                resolve();
            };

            img.onerror = () => {
                reject(new Error('Failed to load image'));
            };

            img.src = base64Data.startsWith('data:') ? base64Data : `data:image/png;base64,${base64Data}`;
        });
    }

    /**
     * Load and display a frame from a URL
     */
    public loadFrameFromUrl(url: string): Promise<void> {
        return new Promise((resolve, reject) => {
            const img = new Image();

            img.onload = () => {
                this.currentFrame = img;
                this.renderFrame();
                resolve();
            };

            img.onerror = () => {
                reject(new Error(`Failed to load image from ${url}`));
            };

            img.src = url;
        });
    }

    /**
     * Render the current frame on the canvas
     */
    private renderFrame(): void {
        if (!this.currentFrame) return;

        // Clear canvas
        this.ctx.fillStyle = '#000000';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        // Calculate scaling to fit canvas while maintaining aspect ratio
        const scale = Math.min(
            this.canvas.width / this.currentFrame.width,
            this.canvas.height / this.currentFrame.height
        );

        const width = this.currentFrame.width * scale;
        const height = this.currentFrame.height * scale;
        const x = (this.canvas.width - width) / 2;
        const y = (this.canvas.height - height) / 2;

        // Draw image
        this.ctx.drawImage(this.currentFrame, x, y, width, height);

        // Update resolution
        this.stats.resolution = `${this.currentFrame.width}x${this.currentFrame.height}`;
        this.updateStats();
    }

    /**
     * Update frame statistics
     */
    public updateFrameStats(fps: number, processingTime: number): void {
        this.stats.fps = fps;
        this.stats.processingTime = processingTime;
        this.stats.timestamp = Date.now();
        this.updateStats();
    }

    /**
     * Display statistics on the page
     */
    private updateStats(): void {
        const timestamp = new Date(this.stats.timestamp).toLocaleTimeString();

        this.statsElement.innerHTML = `
            <div class="stat-item">
                <span class="stat-label">FPS:</span>
                <span class="stat-value">${this.stats.fps.toFixed(1)}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Resolution:</span>
                <span class="stat-value">${this.stats.resolution}</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Processing Time:</span>
                <span class="stat-value">${this.stats.processingTime.toFixed(1)} ms</span>
            </div>
            <div class="stat-item">
                <span class="stat-label">Last Update:</span>
                <span class="stat-value">${timestamp}</span>
            </div>
        `;
    }

    /**
     * Clear the canvas
     */
    public clear(): void {
        this.setupCanvas();
        this.currentFrame = null;
    }
}

export default FrameViewer;
