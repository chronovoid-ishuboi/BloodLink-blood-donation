# BloodLink

BloodLink is a comprehensive, desktop-based blood donation management system built to seamlessly connect blood donors with requesters. Developed as an academic project for the CSE 4402 Visual Programming course, this application leverages advanced location services, optical character recognition (OCR) for identity verification, and a gamified reward system to encourage and streamline the blood donation process.

## Demonstration

**YouTube link:** [Presentation video on YouTube]((2) BloodLink VP Presentation - YouTube)

**Drive link:** [Presentation video on Google Drive](CSE 4402 VP final project- BloodLink_presentation - Google Drive)

## Team Members

| Name | Primary Responsibilities |
| :--- | :--- |
| **Issmam (230041213)** | Project Skeleton, Maps/Geocoding, Gemini/Tesseract OCR, UI Controllers & Shell Integration |
| **Niloy (230041205)** | Database Configuration (Aiven/H2), SQL Migrations, Data Access Objects (DAOs) |
| **Rayed (230041217)** | Data Models, UI Assets (CSS/Fonts), Core Utilities, Business Logic Services & Authentication |

## Key Features

*   **Smart Matching & Location Services:** Dynamically calculates distances between donors, requesters, and hospitals using OpenStreetMap and integrated geocoding.
*   **Automated Identity Verification:** Utilizes both Gemini AI and Tesseract OCR to scan and extract data from National ID (NID) cards, ensuring a secure and verified user base.
*   **Gamification & Rewards:** Features a comprehensive points system where donors earn badges (Bronze, Silver, Gold, Platinum) and redeemable vouchers for their contributions. Includes a live leaderboard.
*   **Role-Based Dashboards:** Dedicated, secure interfaces for Admins, Donors, and Requesters, complete with real-time activity feeds and analytics.
*   **In-App Communication:** Direct messaging and chat functionalities to facilitate rapid coordination during urgent blood requests.
*   **Cloud-Connected:** Fully integrated with an Aiven-hosted MySQL database for persistent, real-time data synchronization across all clients.

## Technology Stack

| Category | Technology |
| :--- | :--- |
| **Language** | Java |
| **Framework** | JavaFX |
| **Build Tool** | Maven |
| **Database** | MySQL (Aiven Cloud) |
| **AI & OCR** | Google Gemini API, Tesseract OCR |
| **Mapping** | OpenStreetMap, Custom HTML/JS Integration, GeoIP |

*Note on Third-Party Services and Local Testing Data:* 
Please be advised that this application integrates several third-party platforms, including the Google Gemini API, Tesseract OCR, and Aiven Cloud. Consequently, certain core functionalities depend entirely on the operational uptime, network traffic, and API quotas of these external providers, which are beyond our direct control. Additionally, the H2 and local database files utilized during the development phase were used strictly for local testing purposes and have been intentionally excluded from this repository.

## Getting Started

### Prerequisites

*   Java Development Kit (JDK) installed and configured.
*   Apache Maven installed.
*   Git installed.

### Installation & Setup

1.  **Clone the repository:**
    ```bash
    git clone [https://github.com/chronovoid-ishuboi/BloodLink-blood-donation.git](https://github.com/chronovoid-ishuboi/BloodLink-blood-donation.git)
    cd BloodLink-blood-donation
    ```

2.  **Configure Environment Variables:**
    To run the application, you must provide your own API keys and database credentials. Create a `local.properties` file in the root directory and configure the following variables:
    ```properties
    # Aiven Database Credentials
    db.url=jdbc:mysql://bloodlink-db-sunbimhaqueniloy-4a1d.b.aivencloud.com:10391/defaultdb?sslMode=REQUIRED&serverTimezone=UTC
    db.user=avnadmin
    db.password=db_password

    # API Keys
    gemini.api.key=gemini_api_key
    ```
    *Note: Ensure `local.properties` remains in `.gitignore` to prevent leaking credentials.*

3.  **Compile and Run:**
    Use Maven to build and launch the application:
    ```bash
    mvn clean install
    mvn javafx:run
    ```
