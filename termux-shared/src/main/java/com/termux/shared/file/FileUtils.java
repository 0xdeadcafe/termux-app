package com.termux.shared.file;

import android.system.Os;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.file.filesystem.FileType;
import com.termux.shared.file.filesystem.FileTypes;
import com.termux.shared.data.DataUtils;
import com.termux.shared.logger.Logger;
import com.termux.shared.errors.Errno;
import com.termux.shared.errors.Error;
import com.termux.shared.errors.FunctionErrno;
import com.termux.shared.errors.FunctionException;
import com.termux.shared.errors.TermuxException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InvalidClassException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class FileUtils {

    /** Required file permissions for the executable file for app usage. Executable file must have read and execute permissions */
    public static final String APP_EXECUTABLE_FILE_PERMISSIONS = "r-x"; // Default: "r-x"
    /** Required file permissions for the working directory for app usage. Working directory must have read and write permissions.
     * Execute permissions should be attempted to be set, but ignored if they are missing */
    public static final String APP_WORKING_DIRECTORY_PERMISSIONS = "rwx"; // Default: "rwx"

    private static final String LOG_TAG = "FileUtils";

    /**
     * Get canonical path.
     *
     * If path is already an absolute path, then it is used as is to get canonical path.
     * If path is not an absolute path and {code prefixForNonAbsolutePath} is not {@code null}, then
     * {code prefixForNonAbsolutePath} + "/" is prefixed before path before getting canonical path.
     * If path is not an absolute path and {code prefixForNonAbsolutePath} is {@code null}, then
     * "/" is prefixed before path before getting canonical path.
     *
     * If an exception is raised to get the canonical path, then absolute path is returned.
     *
     * @param path The {@code path} to convert.
     * @param prefixForNonAbsolutePath Optional prefix path to prefix before non-absolute paths. This
     *                                 can be set to {@code null} if non-absolute paths should
     *                                 be prefixed with "/". The call to {@link File#getCanonicalPath()}
     *                                 will automatically do this anyways.
     * @return Returns the {@code canonical path}.
     */
    public static String getCanonicalPath(String path, final String prefixForNonAbsolutePath) {
        if (path == null) path = "";

        String absolutePath;

        // If path is already an absolute path
        if (path.startsWith("/")) {
            absolutePath = path;
        } else {
            if (prefixForNonAbsolutePath != null)
                absolutePath = prefixForNonAbsolutePath + "/" + path;
            else
                absolutePath = "/" + path;
        }

        try {
            return new File(absolutePath).getCanonicalPath();
        } catch(Exception e) {
        }

        return absolutePath;
    }

    /**
     * Removes one or more forward slashes "//" with single slash "/"
     * Removes "./"
     * Removes "../" (and leading "..")
     * Removes trailing forward slash "/"
     * Note: for security-critical path confinement always prefer
     * {@link File#getCanonicalPath()} over this method.
     *
     * @param path The {@code path} to convert.
     * @return Returns the {@code normalized path}.
     */
    @Nullable
    public static String normalizePath(String path) {
        if (path == null) return null;

        path = path.replaceAll("/+", "/");
        path = path.replaceAll("(\\./|(?:(?:^|/)\\.\\.(?=/|$)))", "");
        path = path.replaceAll("\\.\\./", "");
        path = path.replaceAll("\\./", "");

        if (path.endsWith("/")) {
            path = path.replaceAll("/+$", "");
        }

        return path;
    }

    /**
     * Convert special characters `\/:*?"<>|` to underscore.
     *
     * @param fileName The name to sanitize.
     * @param sanitizeWhitespaces If set to {@code true}, then white space characters ` \t\n` will be
     *                            converted.
     * @param toLower If set to {@code true}, then file name will be converted to lower case.
     * @return Returns the {@code sanitized name}.
     */
    public static String sanitizeFileName(String fileName, boolean sanitizeWhitespaces, boolean toLower) {
        if (fileName == null) return null;

        if (sanitizeWhitespaces)
            fileName = fileName.replaceAll("[\\\\/:*?\"<>| \t\n]", "_");
        else
            fileName = fileName.replaceAll("[\\\\/:*?\"<>|]", "_");

        if (toLower)
            return fileName.toLowerCase();
        else
            return fileName;
    }

    /**
     * Determines whether path is in {@code dirPath}. The {@code dirPath} is not canonicalized and
     * only normalized.
     *
     * @param path The {@code path} to check.
     * @param dirPath The {@code directory path} to check in.
     * @param ensureUnder If set to {@code true}, then it will be ensured that {@code path} is
     *                    under the directory and does not equal it.
     * @return Returns {@code true} if path in {@code dirPath}, otherwise returns {@code false}.
     */
    public static boolean isPathInDirPath(String path, final String dirPath, final boolean ensureUnder) {
        return isPathInDirPaths(path, Collections.singletonList(dirPath), ensureUnder);
    }

    /**
     * Determines whether path is in one of the {@code dirPaths}. The {@code dirPaths} are not
     * canonicalized and only normalized.
     *
     * @param path The {@code path} to check.
     * @param dirPaths The {@code directory paths} to check in.
     * @param ensureUnder If set to {@code true}, then it will be ensured that {@code path} is
     *                    under the directories and does not equal it.
     * @return Returns {@code true} if path in {@code dirPaths}, otherwise returns {@code false}.
     */
    public static boolean isPathInDirPaths(String path, final List<String> dirPaths, final boolean ensureUnder) {
        if (path == null || path.isEmpty() || dirPaths == null || dirPaths.size() < 1) return false;

        try {
            path = new File(path).getCanonicalPath();
        } catch(Exception e) {
            return false;
        }

        boolean isPathInDirPaths;

        for (String dirPath : dirPaths) {
            String normalizedDirPath = normalizePath(dirPath);

            if (ensureUnder)
                isPathInDirPaths = !path.equals(normalizedDirPath) && path.startsWith(normalizedDirPath + "/");
            else
                isPathInDirPaths = path.startsWith(normalizedDirPath + "/");

            if (isPathInDirPaths) return true;
        }

        return false;
    }



    /**
     * Exception-throwing sibling of {@link #validateDirectoryFileEmptyOrOnlyContainsSpecificFiles(String, String, List, boolean)}.
     * @throws TermuxException If directory is not empty or contains files not in {@code ignoredSubFilePaths}, or checking failed.
     */
    public static void validateDirectoryFileEmptyOrOnlyContainsSpecificFilesOrThrow(String label, String filePath,
                                                                                     final List<String> ignoredSubFilePaths,
                                                                                     final boolean ignoreNonExistentFile) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "isDirectoryFileEmptyOrOnlyContainsSpecificFiles"));

        try {
            File file = new File(filePath);
            FileType fileType = getFileType(filePath, false);

            // If file exists but not a directory file
            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                throw new TermuxException(FileUtilsErrno.ERRNO_NON_DIRECTORY_FILE_FOUND.getError(label + "directory", filePath).setLabel(label + "directory"));
            }

            // If file does not exist
            if (fileType == FileType.NO_EXIST) {
                // If checking is to be ignored if file does not exist
                if (ignoreNonExistentFile)
                    return;
                else {
                    label += "directory to check if is empty or only contains specific files";
                    throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(label, filePath).setLabel(label));
                }
            }

            File[] subFiles = file.listFiles();
            if (subFiles == null || subFiles.length == 0)
                return;

            // If sub files exists but no file should be ignored
            if (ignoredSubFilePaths == null || ignoredSubFilePaths.size() == 0)
                throw new TermuxException(FileUtilsErrno.ERRNO_NON_EMPTY_DIRECTORY_FILE.getError(label, filePath));

            // If a sub file does not exist in ignored file path
            if (nonIgnoredSubFileExists(subFiles, ignoredSubFilePaths)) {
                throw new TermuxException(FileUtilsErrno.ERRNO_NON_EMPTY_DIRECTORY_FILE.getError(label, filePath));
            }

        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_VALIDATE_DIRECTORY_EMPTY_OR_ONLY_CONTAINS_SPECIFIC_FILES_FAILED_WITH_EXCEPTION.getError(e, label + "directory", filePath, e.getMessage()));
        }
    }

    /**
     * Check if {@code subFiles} contains contains a file not in {@code ignoredSubFilePaths}.
     *
     * If parent path of an ignored file exists, but ignored file itself does not exist, then directory
     * is not considered empty.
     *
     * This function should ideally not be called by itself but through
     * {@link #validateDirectoryFileEmptyOrOnlyContainsSpecificFiles(String, String, List, boolean)}.
     *
     * @param subFiles The list of files of a directory to check.
     * @param ignoredSubFilePaths The list of absolute file paths under {@code filePath} dir.
     *                            Validation is done for the paths.
     * @return Returns {@code true} if a file was found that did not exist in the {@code ignoredSubFilePaths},
     * otherwise  {@code false}.
     */
    public static boolean nonIgnoredSubFileExists(File[] subFiles, @NonNull List<String> ignoredSubFilePaths) {
        if (subFiles == null || subFiles.length == 0) return false;

        String subFilePath;
        for (File subFile : subFiles) {
            subFilePath = subFile.getAbsolutePath();
            // If sub file does not exist in ignored sub file paths
            if (!ignoredSubFilePaths.contains(subFilePath)) {
                boolean isParentPath = false;
                for (String ignoredSubFilePath : ignoredSubFilePaths) {
                    if (ignoredSubFilePath.startsWith(subFilePath + "/") && fileExists(ignoredSubFilePath, false)) {
                        isParentPath = true;
                        break;
                    }
                }
                // If sub file is not a parent of any existing ignored sub file paths
                if (!isParentPath) {
                    return true;
                }
            }
                
            if (getFileType(subFilePath, false) == FileType.DIRECTORY) {
                // If non ignored sub file found, then early exit, otherwise continue looking
                if (nonIgnoredSubFileExists(subFile.listFiles(), ignoredSubFilePaths))
                     return true;
            }
        }

        return false;
    }



    /**
     * Checks whether a regular file exists at {@code filePath}.
     *
     * @param filePath The {@code path} for regular file to check.
     * @param followLinks The {@code boolean} that decides if symlinks will be followed while
     *                       finding if file exists. Check {@link #getFileType(String, boolean)}
     *                       for details.
     * @return Returns {@code true} if regular file exists, otherwise {@code false}.
     */
    public static boolean regularFileExists(final String filePath, final boolean followLinks) {
        return getFileType(filePath, followLinks) == FileType.REGULAR;
    }

    /**
     * Checks whether a directory file exists at {@code filePath}.
     *
     * @param filePath The {@code path} for directory file to check.
     * @param followLinks The {@code boolean} that decides if symlinks will be followed while
     *                       finding if file exists. Check {@link #getFileType(String, boolean)}
     *                       for details.
     * @return Returns {@code true} if directory file exists, otherwise {@code false}.
     */
    public static boolean directoryFileExists(final String filePath, final boolean followLinks) {
        return getFileType(filePath, followLinks) == FileType.DIRECTORY;
    }

    /**
     * Checks whether a symlink file exists at {@code filePath}.
     *
     * @param filePath The {@code path} for symlink file to check.
     * @return Returns {@code true} if symlink file exists, otherwise {@code false}.
     */
    public static boolean symlinkFileExists(final String filePath) {
        return getFileType(filePath, false) == FileType.SYMLINK;
    }

    /**
     * Checks whether a regular or directory file exists at {@code filePath}.
     *
     * @param filePath The {@code path} for regular file to check.
     * @param followLinks The {@code boolean} that decides if symlinks will be followed while
     *                       finding if file exists. Check {@link #getFileType(String, boolean)}
     *                       for details.
     * @return Returns {@code true} if regular or directory file exists, otherwise {@code false}.
     */
    public static boolean regularOrDirectoryFileExists(final String filePath, final boolean followLinks) {
        FileType fileType = getFileType(filePath, followLinks);
        return fileType == FileType.REGULAR || fileType == FileType.DIRECTORY;
    }

    /**
     * Checks whether any file exists at {@code filePath}.
     *
     * @param filePath The {@code path} for file to check.
     * @param followLinks The {@code boolean} that decides if symlinks will be followed while
     *                       finding if file exists. Check {@link #getFileType(String, boolean)}
     *                       for details.
     * @return Returns {@code true} if file exists, otherwise {@code false}.
     */
    public static boolean fileExists(final String filePath, final boolean followLinks) {
        return getFileType(filePath, followLinks) != FileType.NO_EXIST;
    }

    /**
     * Get the type of file that exists at {@code filePath}.
     *
     * This function is a wrapper for
     * {@link FileTypes#getFileType(String, boolean)}
     *
     * @param filePath The {@code path} for file to check.
     * @param followLinks The {@code boolean} that decides if symlinks will be followed while
     *                       finding type. If set to {@code true}, then type of symlink target will
     *                       be returned if file at {@code filePath} is a symlink. If set to
     *                       {@code false}, then type of file at {@code filePath} itself will be
     *                       returned.
     * @return Returns the {@link FileType} of file.
     */
    @NonNull
    public static FileType getFileType(final String filePath, final boolean followLinks) {
        return FileTypes.getFileType(filePath, followLinks);
    }



    /**
     * Exception-throwing sibling of {@link #validateRegularFileExistenceAndPermissions(String, String, String, String, boolean, boolean, boolean)}.
     * @throws TermuxException If path is not a regular file, or validating permissions failed.
     */
    public static void validateRegularFileExistenceAndPermissionsOrThrow(String label, final String filePath, final String parentDirPath,
                                                                          final String permissionsToCheck, final boolean setPermissions, final boolean setMissingPermissionsOnly,
                                                                          final boolean ignoreErrorsIfPathIsUnderParentDirPath) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "regular file path", "validateRegularFileExistenceAndPermissions"));

        try {
            FileType fileType = getFileType(filePath, false);

            // If file exists but not a regular file
            if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
                throw new TermuxException(FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(label + "file", filePath).setLabel(label + "file"));
            }

            boolean isPathUnderParentDirPath = false;
            if (parentDirPath != null) {
                // The path can only be under parent directory path
                isPathUnderParentDirPath = isPathInDirPath(filePath, parentDirPath, true);
            }

            // If setPermissions is enabled and path is a regular file
            if (setPermissions && permissionsToCheck != null && fileType == FileType.REGULAR) {
                // If there is not parentDirPath restriction or path is under parentDirPath
                if (parentDirPath == null || (isPathUnderParentDirPath && getFileType(parentDirPath, false) == FileType.DIRECTORY)) {
                    if (setMissingPermissionsOnly)
                        setMissingFilePermissions(label + "file", filePath, permissionsToCheck);
                    else
                        setFilePermissions(label + "file", filePath, permissionsToCheck);
                }
            }

            // If path is not a regular file
            // Regular files cannot be automatically created so we do not ignore if missing
            if (fileType != FileType.REGULAR) {
                label += "regular file";
                throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(label, filePath).setLabel(label));
            }

            // If there is not parentDirPath restriction or path is not under parentDirPath or
            // if permission errors must not be ignored for paths under parentDirPath
            if (parentDirPath == null || !isPathUnderParentDirPath || !ignoreErrorsIfPathIsUnderParentDirPath) {
                if (permissionsToCheck != null) {
                    // Check if permissions are missing
                    checkMissingFilePermissionsOrThrow(label + "regular", filePath, permissionsToCheck, false);
                }
            }
        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_VALIDATE_FILE_EXISTENCE_AND_PERMISSIONS_FAILED_WITH_EXCEPTION.getError(e, label + "file", filePath, e.getMessage()));
        }
    }

    /**
     * Exception-throwing sibling of {@link #validateDirectoryFileExistenceAndPermissions(String, String, String, boolean, String, boolean, boolean, boolean, boolean)}.
     * @throws TermuxException If path is not a directory file, failed to create it, or validating permissions failed.
     */
    public static void validateDirectoryFileExistenceAndPermissionsOrThrow(String label, final String filePath, final String parentDirPath, final boolean createDirectoryIfMissing,
                                                                            final String permissionsToCheck, final boolean setPermissions, final boolean setMissingPermissionsOnly,
                                                                            final boolean ignoreErrorsIfPathIsInParentDirPath, final boolean ignoreIfNotExecutable) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "directory file path", "validateDirectoryExistenceAndPermissions"));

        try {
            File file = new File(filePath);
            FileType fileType = getFileType(filePath, false);

            // If file exists but not a directory file
            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                throw new TermuxException(FileUtilsErrno.ERRNO_NON_DIRECTORY_FILE_FOUND.getError(label + "directory", filePath).setLabel(label + "directory"));
            }

            boolean isPathInParentDirPath = false;
            if (parentDirPath != null) {
                // The path can be equal to parent directory path or under it
                isPathInParentDirPath = isPathInDirPath(filePath, parentDirPath, false);
            }

            if (createDirectoryIfMissing || setPermissions) {
                // If there is not parentDirPath restriction or path is in parentDirPath
                if (parentDirPath == null || (isPathInParentDirPath && getFileType(parentDirPath, false) == FileType.DIRECTORY)) {
                    // If createDirectoryIfMissing is enabled and no file exists at path, then create directory
                    if (createDirectoryIfMissing && fileType == FileType.NO_EXIST) {
                        Logger.logVerbose(LOG_TAG, "Creating " + label + "directory file at path \"" + filePath + "\"");
                        // Create directory and update fileType if successful, otherwise return with error
                        // It "might" be possible that mkdirs returns false even though directory was created
                        boolean result = file.mkdirs();
                        fileType = getFileType(filePath, false);
                        if (!result && fileType != FileType.DIRECTORY)
                            throw new TermuxException(FileUtilsErrno.ERRNO_CREATING_FILE_FAILED.getError(label + "directory file", filePath));
                    }

                    // If setPermissions is enabled and path is a directory
                    if (setPermissions && permissionsToCheck != null && fileType == FileType.DIRECTORY) {
                        if (setMissingPermissionsOnly)
                            setMissingFilePermissions(label + "directory", filePath, permissionsToCheck);
                        else
                            setFilePermissions(label + "directory", filePath, permissionsToCheck);
                    }
                }
            }

            // If there is not parentDirPath restriction or path is not in parentDirPath or
            // if existence or permission errors must not be ignored for paths in parentDirPath
            if (parentDirPath == null || !isPathInParentDirPath || !ignoreErrorsIfPathIsInParentDirPath) {
                // If path is not a directory
                // Directories can be automatically created so we can ignore if missing with above check
                if (fileType != FileType.DIRECTORY) {
                    label += "directory";
                    throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(label, filePath).setLabel(label));
                }

                if (permissionsToCheck != null) {
                    // Check if permissions are missing
                    checkMissingFilePermissionsOrThrow(label + "directory", filePath, permissionsToCheck, ignoreIfNotExecutable);
                }
            }
        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_VALIDATE_DIRECTORY_EXISTENCE_AND_PERMISSIONS_FAILED_WITH_EXCEPTION.getError(e, label + "directory file", filePath, e.getMessage()));
        }
    }



    /**
     * Exception-throwing sibling of {@link #createRegularFile(String)}.
     * @throws TermuxException If path is not a regular file or failed to create it.
     */
    public static void createRegularFileOrThrow(final String filePath) throws TermuxException {
        createRegularFileOrThrow(null, filePath);
    }

    /**
     * Exception-throwing sibling of {@link #createRegularFile(String, String)}.
     * @throws TermuxException If path is not a regular file or failed to create it.
     */
    public static void createRegularFileOrThrow(final String label, final String filePath) throws TermuxException {
        createRegularFileOrThrow(label, filePath, null, false, false);
    }

    /**
     * Exception-throwing sibling of {@link #createRegularFile(String, String, String, boolean, boolean)}.
     * @throws TermuxException If path is not a regular file, failed to create it, or validating permissions failed.
     */
    public static void createRegularFileOrThrow(String label, final String filePath,
                                                 final String permissionsToCheck, final boolean setPermissions, final boolean setMissingPermissionsOnly) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "createRegularFile"));

        File file = new File(filePath);
        FileType fileType = getFileType(filePath, false);

        // If file exists but not a regular file
        if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
            throw new TermuxException(FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(label + "file", filePath).setLabel(label + "file"));
        }

        // If regular file already exists
        if (fileType == FileType.REGULAR) {
            return;
        }

        // Create the file parent directory
        createParentDirectoryFileOrThrow(label + "regular file parent", filePath);

        try {
            Logger.logVerbose(LOG_TAG, "Creating " + label + "regular file at path \"" + filePath + "\"");

            if (!file.createNewFile())
                throw new TermuxException(FileUtilsErrno.ERRNO_CREATING_FILE_FAILED.getError(label + "regular file", filePath));
        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_CREATING_FILE_FAILED_WITH_EXCEPTION.getError(e, label + "regular file", filePath, e.getMessage()));
        }

        validateRegularFileExistenceAndPermissionsOrThrow(label, filePath,
            null,
            permissionsToCheck, setPermissions, setMissingPermissionsOnly,
            false);
    }



    /**
     * Exception-throwing sibling of {@link #createParentDirectoryFile(String, String)}.
     * @throws TermuxException If parent path is not a directory file or failed to create it.
     */
    public static void createParentDirectoryFileOrThrow(final String label, final String filePath) throws TermuxException {
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "createParentDirectoryFile"));

        File file = new File(filePath);
        String fileParentPath = file.getParent();

        if (fileParentPath != null)
            createDirectoryFileOrThrow(label, fileParentPath, null, false, false);
    }

    /**
     * Create a directory file at path.
     *
     * This function is a wrapper for
     * {@link #validateDirectoryFileExistenceAndPermissions(String, String, String, boolean, String, boolean, boolean, boolean, boolean)}.
     *
     * @param filePath The {@code path} for directory file to create.
     * @return Returns the {@code error} if path is not a directory file or failed to create it,
     * otherwise {@code null}.
     */
    public static void createDirectoryFileOrThrow(final String filePath) throws TermuxException {
        createDirectoryFileOrThrow(null, filePath);
    }

    /**
     * Exception-throwing sibling of {@link #createDirectoryFile(String, String)}.
     * @throws TermuxException If path is not a directory file or failed to create it.
     */
    public static void createDirectoryFileOrThrow(final String label, final String filePath) throws TermuxException {
        createDirectoryFileOrThrow(label, filePath, null, false, false);
    }

    /**
     * Exception-throwing sibling of {@link #createDirectoryFile(String, String, String, boolean, boolean)}.
     * @throws TermuxException If path is not a directory file, failed to create it, or validating permissions failed.
     */
    public static void createDirectoryFileOrThrow(final String label, final String filePath,
                                                   final String permissionsToCheck, final boolean setPermissions, final boolean setMissingPermissionsOnly) throws TermuxException {
        validateDirectoryFileExistenceAndPermissionsOrThrow(label, filePath,
            null, true,
            permissionsToCheck, setPermissions, setMissingPermissionsOnly,
            false, false);
    }



    /**
     * Exception-throwing sibling of {@link #createSymlinkFile(String, String)}.
     * @throws TermuxException If path is not a symlink file or failed to create it.
     */
    public static void createSymlinkFileOrThrow(final String targetFilePath, final String destFilePath) throws TermuxException {
        createSymlinkFileOrThrow(null, targetFilePath, destFilePath, true, true, true);
    }

    /**
     * Exception-throwing sibling of {@link #createSymlinkFile(String, String, String)}.
     * @throws TermuxException If path is not a symlink file or failed to create it.
     */
    public static void createSymlinkFileOrThrow(String label, final String targetFilePath, final String destFilePath) throws TermuxException {
        createSymlinkFileOrThrow(label, targetFilePath, destFilePath, true, true, true);
    }

    /**
     * Exception-throwing sibling of {@link #createSymlinkFile(String, String, String, boolean, boolean, boolean)}.
     * @throws TermuxException If path is not a symlink file, failed to create it, or validating permissions failed.
     */
    public static void createSymlinkFileOrThrow(String label, final String targetFilePath, final String destFilePath,
                                                 final boolean allowDangling, final boolean overwrite, final boolean overwriteOnlyIfDestIsASymlink) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (targetFilePath == null || targetFilePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "target file path", "createSymlinkFile"));
        if (destFilePath == null || destFilePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "destination file path", "createSymlinkFile"));

        try {
            File destFile = new File(destFilePath);

            String targetFileAbsolutePath = targetFilePath;
            // If target path is relative instead of absolute
            if (!targetFilePath.startsWith("/")) {
                String destFileParentPath = destFile.getParent();
                if (destFileParentPath != null)
                    targetFileAbsolutePath = destFileParentPath + "/" +  targetFilePath;
            }

            FileType targetFileType = getFileType(targetFileAbsolutePath, false);
            FileType destFileType = getFileType(destFilePath, false);

            // If target file does not exist
            if (targetFileType == FileType.NO_EXIST) {
                // If dangling symlink should not be allowed, then return with error
                if (!allowDangling) {
                    label += "symlink target file";
                    throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(label, targetFileAbsolutePath).setLabel(label));
                }
            }

            // If destination exists
            if (destFileType != FileType.NO_EXIST) {
                // If destination must not be overwritten
                if (!overwrite) {
                    return;
                }

                // If overwriteOnlyIfDestIsASymlink is enabled but destination file is not a symlink
                if (overwriteOnlyIfDestIsASymlink && destFileType != FileType.SYMLINK)
                    throw new TermuxException(FileUtilsErrno.ERRNO_CANNOT_OVERWRITE_A_NON_SYMLINK_FILE_TYPE.getError(label + " file", destFilePath, targetFilePath, destFileType.getName()));

                // Delete the destination file
                deleteFileOrThrow(label + "symlink destination", destFilePath, true);
            } else {
                // Create the destination file parent directory
                createParentDirectoryFileOrThrow(label + "symlink destination file parent", destFilePath);
            }

            // create a symlink at destFilePath to targetFilePath
            Logger.logVerbose(LOG_TAG, "Creating " + label + "symlink file at path \"" + destFilePath + "\" to \"" + targetFilePath + "\"");
            Os.symlink(targetFilePath, destFilePath);
        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_CREATING_SYMLINK_FILE_FAILED_WITH_EXCEPTION.getError(e, label + "symlink file", destFilePath, targetFilePath, e.getMessage()));
        }
    }



    /**
     * Exception-throwing sibling of {@link #copyRegularFile(String, String, String, boolean)}.
     * @throws TermuxException If copy was not successful.
     */
    public static void copyRegularFileOrThrow(final String label, final String srcFilePath, final String destFilePath, final boolean ignoreNonExistentSrcFile) throws TermuxException {
        copyOrMoveFileOrThrow(label, srcFilePath, destFilePath, false, ignoreNonExistentSrcFile, FileType.REGULAR.getValue(), true, true);
    }

    /**
     * Exception-throwing sibling of {@link #moveRegularFile(String, String, String, boolean)}.
     * @throws TermuxException If move was not successful.
     */
    public static void moveRegularFileOrThrow(final String label, final String srcFilePath, final String destFilePath, final boolean ignoreNonExistentSrcFile) throws TermuxException {
        copyOrMoveFileOrThrow(label, srcFilePath, destFilePath, true, ignoreNonExistentSrcFile, FileType.REGULAR.getValue(), true, true);
    }

    /**
     * Exception-throwing sibling of {@link #copyDirectoryFile(String, String, String, boolean)}.
     * @throws TermuxException If copy was not successful.
     */
    public static void copyDirectoryFileOrThrow(final String label, final String srcFilePath, final String destFilePath, final boolean ignoreNonExistentSrcFile) throws TermuxException {
        copyOrMoveFileOrThrow(label, srcFilePath, destFilePath, false, ignoreNonExistentSrcFile, FileType.DIRECTORY.getValue(), true, true);
    }

    /**
     * Exception-throwing sibling of {@link #moveDirectoryFile(String, String, String, boolean)}.
     * @throws TermuxException If move was not successful.
     */
    public static void moveDirectoryFileOrThrow(final String label, final String srcFilePath, final String destFilePath, final boolean ignoreNonExistentSrcFile) throws TermuxException {
        copyOrMoveFileOrThrow(label, srcFilePath, destFilePath, true, ignoreNonExistentSrcFile, FileType.DIRECTORY.getValue(), true, true);
    }

    /**
     * Exception-throwing sibling of {@link #copySymlinkFile(String, String, String, boolean)}.
     * @throws TermuxException If copy was not successful.
     */
    public static void copySymlinkFileOrThrow(final String label, final String srcFilePath, final String destFilePath, final boolean ignoreNonExistentSrcFile) throws TermuxException {
        copyOrMoveFileOrThrow(label, srcFilePath, destFilePath, false, ignoreNonExistentSrcFile, FileType.SYMLINK.getValue(), true, true);
    }

    /**
     * Exception-throwing sibling of {@link #moveSymlinkFile(String, String, String, boolean)}.
     * @throws TermuxException If move was not successful.
     */
    public static void moveSymlinkFileOrThrow(final String label, final String srcFilePath, final String destFilePath, final boolean ignoreNonExistentSrcFile) throws TermuxException {
        copyOrMoveFileOrThrow(label, srcFilePath, destFilePath, true, ignoreNonExistentSrcFile, FileType.SYMLINK.getValue(), true, true);
    }

    /**
     * Exception-throwing sibling of {@link #copyFile(String, String, String, boolean)}.
     * @throws TermuxException If copy was not successful.
     */
    public static void copyFileOrThrow(final String label, final String srcFilePath, final String destFilePath, final boolean ignoreNonExistentSrcFile) throws TermuxException {
        copyOrMoveFileOrThrow(label, srcFilePath, destFilePath, false, ignoreNonExistentSrcFile, FileTypes.FILE_TYPE_NORMAL_FLAGS, true, true);
    }

    /**
     * Exception-throwing sibling of {@link #moveFile(String, String, String, boolean)}.
     * @throws TermuxException If move was not successful.
     */
    public static void moveFileOrThrow(final String label, final String srcFilePath, final String destFilePath, final boolean ignoreNonExistentSrcFile) throws TermuxException {
        copyOrMoveFileOrThrow(label, srcFilePath, destFilePath, true, ignoreNonExistentSrcFile, FileTypes.FILE_TYPE_NORMAL_FLAGS, true, true);
    }

    /**
     * Exception-throwing sibling of {@link #copyOrMoveFile(String, String, String, boolean, boolean, int, boolean, boolean)}.
     * @throws TermuxException If copy or move was not successful.
     */
    public static void copyOrMoveFileOrThrow(String label, final String srcFilePath, final String destFilePath,
                                              final boolean moveFile, final boolean ignoreNonExistentSrcFile, int allowedFileTypeFlags,
                                              final boolean overwrite, final boolean overwriteOnlyIfDestSameFileTypeAsSrc) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (srcFilePath == null || srcFilePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "source file path", "copyOrMoveFile"));
        if (destFilePath == null || destFilePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "destination file path", "copyOrMoveFile"));

        String mode = (moveFile ? "Moving" : "Copying");
        String modePast = (moveFile ? "moved" : "copied");

        try {
            Logger.logVerbose(LOG_TAG, mode + " " + label + "source file from \"" + srcFilePath + "\" to destination \"" + destFilePath + "\"");

            File srcFile = new File(srcFilePath);
            File destFile = new File(destFilePath);

            FileType srcFileType = getFileType(srcFilePath, false);
            FileType destFileType = getFileType(destFilePath, false);

            String srcFileCanonicalPath = srcFile.getCanonicalPath();
            String destFileCanonicalPath = destFile.getCanonicalPath();

            // If source file does not exist
            if (srcFileType == FileType.NO_EXIST) {
                // If copy or move is to be ignored if source file is not found
                if (ignoreNonExistentSrcFile)
                    return;
                    // Else return with error
                else {
                    label += "source file";
                    throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(label, srcFilePath).setLabel(label));
                }
            }

            // If the file type of the source file does not exist in the allowedFileTypeFlags, then return with error
            if ((allowedFileTypeFlags & srcFileType.getValue()) <= 0)
                throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_AN_ALLOWED_FILE_TYPE.getError(label + "source file meant to be " + modePast, srcFilePath, FileTypes.convertFileTypeFlagsToNamesString(allowedFileTypeFlags)));

            // If source and destination file path are the same
            if (srcFileCanonicalPath.equals(destFileCanonicalPath))
                throw new TermuxException(FileUtilsErrno.ERRNO_COPYING_OR_MOVING_FILE_TO_SAME_PATH.getError(mode + " " + label + "source file", srcFilePath, destFilePath));

            // If destination exists
            if (destFileType != FileType.NO_EXIST) {
                // If destination must not be overwritten
                if (!overwrite) {
                    return;
                }

                // If overwriteOnlyIfDestSameFileTypeAsSrc is enabled but destination file does not match source file type
                if (overwriteOnlyIfDestSameFileTypeAsSrc && destFileType != srcFileType)
                    throw new TermuxException(FileUtilsErrno.ERRNO_CANNOT_OVERWRITE_A_DIFFERENT_FILE_TYPE.getError(label + "source file", mode.toLowerCase(), srcFilePath, destFilePath, destFileType.getName(), srcFileType.getName()));

                // Delete the destination file
                deleteFileOrThrow(label + "destination", destFilePath, true);
            }


            // Copy or move source file to dest
            boolean copyFile = !moveFile;

            // If moveFile is true
            if (moveFile) {
                // We first try to rename source file to destination file to save a copy operation in case both source and destination are on the same filesystem
                Logger.logVerbose(LOG_TAG, "Attempting to rename source to destination.");

                // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/ojluni/src/main/java/java/io/UnixFileSystem.java;l=358
                // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r3:libcore/luni/src/main/java/android/system/Os.java;l=512
                // Uses File.getPath() to get the path of source and destination and not the canonical path
                if (!srcFile.renameTo(destFile)) {
                    // If destination directory is a subdirectory of the source directory
                    // Copying is still allowed by copyDirectory() by excluding destination directory files
                    if (srcFileType == FileType.DIRECTORY && destFileCanonicalPath.startsWith(srcFileCanonicalPath + File.separator))
                        throw new TermuxException(FileUtilsErrno.ERRNO_CANNOT_MOVE_DIRECTORY_TO_SUB_DIRECTORY_OF_ITSELF.getError(label + "source directory", srcFilePath, destFilePath));

                    // If rename failed, then we copy
                    Logger.logVerbose(LOG_TAG, "Renaming " + label + "source file to destination failed, attempting to copy.");
                    copyFile = true;
                }
            }

            // If moveFile is false or renameTo failed while moving
            if (copyFile) {
                Logger.logVerbose(LOG_TAG, "Attempting to copy source to destination.");

                // Create the dest file parent directory
                createParentDirectoryFileOrThrow(label + "dest file parent", destFilePath);

                if (srcFileType == FileType.DIRECTORY) {
                    // Walk the source tree and recreate under dest (symlinks copied as-is, not followed)
                    final Path srcPath = srcFile.toPath();
                    final Path dstPath = destFile.toPath();
                    try (java.util.stream.Stream<Path> tree = Files.walk(srcPath)) {
                        for (java.util.Iterator<Path> it = tree.iterator(); it.hasNext(); ) {
                            Path s = it.next();
                            Path d = dstPath.resolve(srcPath.relativize(s));
                            if (Files.isDirectory(s, LinkOption.NOFOLLOW_LINKS))
                                Files.createDirectories(d);
                            else
                                Files.copy(s, d, StandardCopyOption.REPLACE_EXISTING, LinkOption.NOFOLLOW_LINKS);
                        }
                    }
                } else {
                    // Handles both regular files and symlinks: NOFOLLOW_LINKS preserves a symlink
                    // as a symlink (copying it, not its target) rather than dereferencing it.
                    java.nio.file.Files.copy(srcFile.toPath(), destFile.toPath(), LinkOption.NOFOLLOW_LINKS, StandardCopyOption.REPLACE_EXISTING);
                }
            }

            // If source file had to be moved
            if (moveFile) {
                // Delete the source file since copying would have succeeded
                deleteFileOrThrow(label + "source", srcFilePath, true);
            }

            Logger.logVerbose(LOG_TAG, mode + " successful.");
        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_COPYING_OR_MOVING_FILE_FAILED_WITH_EXCEPTION.getError(e, mode + " " + label + "file", srcFilePath, destFilePath, e.getMessage()));
        }
    }



    /**
     * Exception-throwing sibling of {@link #deleteRegularFile(String, String, boolean)}.
     * @throws TermuxException If deletion was not successful.
     */
    public static void deleteRegularFileOrThrow(String label, final String filePath, final boolean ignoreNonExistentFile) throws TermuxException {
        deleteFileOrThrow(label, filePath, ignoreNonExistentFile, false, FileType.REGULAR.getValue());
    }

    /**
     * Exception-throwing sibling of {@link #deleteDirectoryFile(String, String, boolean)}.
     * @throws TermuxException If deletion was not successful.
     */
    public static void deleteDirectoryFileOrThrow(String label, final String filePath, final boolean ignoreNonExistentFile) throws TermuxException {
        deleteFileOrThrow(label, filePath, ignoreNonExistentFile, false, FileType.DIRECTORY.getValue());
    }

    /**
     * Exception-throwing sibling of {@link #deleteSymlinkFile(String, String, boolean)}.
     * @throws TermuxException If deletion was not successful.
     */
    public static void deleteSymlinkFileOrThrow(String label, final String filePath, final boolean ignoreNonExistentFile) throws TermuxException {
        deleteFileOrThrow(label, filePath, ignoreNonExistentFile, false, FileType.SYMLINK.getValue());
    }

    /**
     * Exception-throwing sibling of {@link #deleteSocketFile(String, String, boolean)}.
     * @throws TermuxException If deletion was not successful.
     */
    public static void deleteSocketFileOrThrow(String label, final String filePath, final boolean ignoreNonExistentFile) throws TermuxException {
        deleteFileOrThrow(label, filePath, ignoreNonExistentFile, false, FileType.SOCKET.getValue());
    }

    /**
     * Exception-throwing sibling of {@link #deleteFile(String, String, boolean)}.
     * @throws TermuxException If deletion was not successful.
     */
    public static void deleteFileOrThrow(String label, final String filePath, final boolean ignoreNonExistentFile) throws TermuxException {
        deleteFileOrThrow(label, filePath, ignoreNonExistentFile, false, FileTypes.FILE_TYPE_NORMAL_FLAGS);
    }

    /**
     * Delete {@code path} and, if it is a directory, everything under it, using plain
     * {@code java.nio.file} APIs. Replaces Guava's
     * {@code MoreFiles.deleteRecursively(path, RecursiveDeleteOption.ALLOW_INSECURE)}.
     * <p/>
     * Symlinks are not followed: a symlink found during the walk is deleted itself (as a single
     * file), not its target, matching Guava's default (non-"ALLOW_INSECURE"-related) behaviour.
     * Unlike Guava's version, this fails fast on the first error encountered rather than
     * collecting every failure across the tree into one exception with suppressed throwables --
     * acceptable here since every caller already wraps this in a generic error path.
     */
    private static void deleteRecursivelyNio(Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;

        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                    if (exc != null) throw exc;
                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Files.delete(path);
        }
    }

    /**
     * Delete everything directly and recursively under {@code dir}, but not {@code dir} itself.
     * Replaces Guava's
     * {@code MoreFiles.deleteDirectoryContents(path, RecursiveDeleteOption.ALLOW_INSECURE)}.
     */
    private static void deleteDirectoryContentsNio(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                deleteRecursivelyNio(entry);
            }
        }
    }

    /**
     * Exception-throwing sibling of {@link #deleteFile(String, String, boolean, boolean, int)}.
     * @throws TermuxException If deletion was not successful.
     */
    public static void deleteFileOrThrow(String label, final String filePath, final boolean ignoreNonExistentFile, final boolean ignoreWrongFileType, int allowedFileTypeFlags) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "deleteFile"));

        try {
            File file = new File(filePath);
            FileType fileType = getFileType(filePath, false);

            Logger.logVerbose(LOG_TAG, "Processing delete of " + label + "file at path \"" + filePath + "\" of type \"" + fileType.getName() + "\"");

            // If file does not exist
            if (fileType == FileType.NO_EXIST) {
                // If delete is to be ignored if file does not exist
                if (ignoreNonExistentFile)
                    return;
                    // Else return with error
                else {
                    label += "file meant to be deleted";
                    throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(label, filePath).setLabel(label));
                }
            }

            // If the file type of the file does not exist in the allowedFileTypeFlags
            if ((allowedFileTypeFlags & fileType.getValue()) <= 0) {
                // If wrong file type is to be ignored
                if (ignoreWrongFileType) {
                    Logger.logVerbose(LOG_TAG, "Ignoring deletion of " + label + "file at path \"" + filePath + "\" of type \"" + fileType.getName() + "\" not matching allowed file types: " + FileTypes.convertFileTypeFlagsToNamesString(allowedFileTypeFlags));
                    return;
                }

                // Else return with error
                throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_AN_ALLOWED_FILE_TYPE.getError(label + "file meant to be deleted", filePath, fileType.getName(), FileTypes.convertFileTypeFlagsToNamesString(allowedFileTypeFlags)));
            }

            Logger.logVerbose(LOG_TAG, "Deleting " + label + "file at path \"" + filePath + "\"");

            // If an exception is thrown mid-walk, files/directories already removed stay removed;
            // the walk fails fast on the first error rather than collecting every failure, which
            // is fine here since the caller already wraps this in a generic error path.
            deleteRecursivelyNio(file.toPath());

            // If file still exists after deleting it
            fileType = getFileType(filePath, false);
            if (fileType != FileType.NO_EXIST)
                throw new TermuxException(FileUtilsErrno.ERRNO_FILE_STILL_EXISTS_AFTER_DELETING.getError(label + "file meant to be deleted", filePath));
        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_DELETING_FILE_FAILED_WITH_EXCEPTION.getError(e, label + "file", filePath, e.getMessage()));
        }
    }



    /**
     * Exception-throwing sibling of {@link #clearDirectory(String)}.
     * @throws TermuxException If clearing was not successful.
     */
    public static void clearDirectoryOrThrow(String filePath) throws TermuxException {
        clearDirectoryOrThrow(null, filePath);
    }

    /**
     * Exception-throwing sibling of {@link #clearDirectory(String, String)}.
     * @throws TermuxException If clearing was not successful.
     */
    public static void clearDirectoryOrThrow(String label, final String filePath) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "clearDirectory"));

        try {
            Logger.logVerbose(LOG_TAG, "Clearing " + label + "directory at path \"" + filePath + "\"");

            File file = new File(filePath);
            FileType fileType = getFileType(filePath, false);

            // If file exists but not a directory file
            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                throw new TermuxException(FileUtilsErrno.ERRNO_NON_DIRECTORY_FILE_FOUND.getError(label + "directory", filePath).setLabel(label + "directory"));
            }

            // If directory exists, clear its contents
            if (fileType == FileType.DIRECTORY) {
                deleteDirectoryContentsNio(file.toPath());
            }
            // Else create it
            else {
                createDirectoryFileOrThrow(label, filePath);
            }
        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_CLEARING_DIRECTORY_FAILED_WITH_EXCEPTION.getError(e, label + "directory", filePath, e.getMessage()));
        }
    }

    /**
     * Exception-throwing sibling of the former {@code deleteFilesOlderThanXDays} method.
     * @throws TermuxException If deleting was not successful.
     */
    public static void deleteFilesOlderThanXDaysOrThrow(String label, final String filePath, int days, final boolean ignoreNonExistentFile, int allowedFileTypeFlags) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "deleteFilesOlderThanXDays"));
        if (days < 0) throw new TermuxException(FunctionErrno.ERRNO_INVALID_PARAMETER.getError(label + "days", "deleteFilesOlderThanXDays", " It must be >= 0."));

        try {
            Logger.logVerbose(LOG_TAG, "Deleting files under " + label + "directory at path \"" + filePath + "\" older than " + days + " days");

            File file = new File(filePath);
            FileType fileType = getFileType(filePath, false);

            // If file exists but not a directory file
            if (fileType != FileType.NO_EXIST && fileType != FileType.DIRECTORY) {
                throw new TermuxException(FileUtilsErrno.ERRNO_NON_DIRECTORY_FILE_FOUND.getError(label + "directory", filePath).setLabel(label + "directory"));
            }

            // If file does not exist
            if (fileType == FileType.NO_EXIST) {
                if (ignoreNonExistentFile) return;
                label += "directory under which files had to be deleted";
                throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(label, filePath).setLabel(label));
            }

            // Collect files (not directories) whose mtime predates the cutoff, then delete them.
            Instant cutoff = Instant.now().minus(days, ChronoUnit.DAYS);
            List<Path> toDelete = new ArrayList<>();
            try (java.util.stream.Stream<Path> tree = Files.walk(file.toPath())) {
                for (java.util.Iterator<Path> it = tree.iterator(); it.hasNext(); ) {
                    Path p = it.next();
                    if (p.equals(file.toPath())) continue;
                    if (Files.isDirectory(p, LinkOption.NOFOLLOW_LINKS)) continue;
                    if (Files.getLastModifiedTime(p, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff))
                        toDelete.add(p);
                }
            }
            for (Path p : toDelete)
                deleteFileOrThrow(label + "directory sub", p.toString(), true, true, allowedFileTypeFlags);
        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_DELETING_FILES_OLDER_THAN_X_DAYS_FAILED_WITH_EXCEPTION.getError(e, label + "directory", filePath, days, e.getMessage()));
        }
    }


    /**
     * Exception-throwing sibling of {@link #readTextFromFile(String, String, Charset, StringBuilder, boolean)}.
     * Reads a text {@link String} from file at path with a specific {@link Charset}.
     *
     * @param label The optional label for file to read. This can optionally be {@code null}.
     * @param filePath The {@code path} for file to read.
     * @param charset The {@link Charset} of the file. If this is {@code null},
     *                then default {@link Charset} will be used.
     * @param ignoreNonExistentFile The {@code boolean} that decides if it should be considered an
     *                              error if file to read doesn't exist.
     * @return Returns the file content, or an empty {@link String} if the file did not exist and
     *         {@code ignoreNonExistentFile} was {@code true}.
     * @throws TermuxException If reading was not successful.
     */
    public static String readTextFromFileOrThrow(String label, final String filePath, Charset charset, final boolean ignoreNonExistentFile) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "readStringFromFile"));

        Logger.logVerbose(LOG_TAG, "Reading text from " + label + "file at path \"" + filePath + "\"");

        FileType fileType = getFileType(filePath, false);

        // If file exists but not a regular file
        if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
            throw new TermuxException(FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(label + "file", filePath).setLabel(label + "file"));
        }

        // If file does not exist
        if (fileType == FileType.NO_EXIST) {
            if (ignoreNonExistentFile)
                return "";
            else {
                label += "file meant to be read";
                throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(label, filePath).setLabel(label));
            }
        }

        if (charset == null) charset = Charset.defaultCharset();

        // Check if charset is supported
        isCharsetSupportedOrThrow(charset);

        StringBuilder dataStringBuilder = new StringBuilder();
        FileInputStream fileInputStream = null;
        BufferedReader bufferedReader = null;
        try {
            // Read text from file
            fileInputStream = new FileInputStream(filePath);
            bufferedReader = new BufferedReader(new InputStreamReader(fileInputStream, charset));

            String receiveString;

            boolean firstLine = true;
            while ((receiveString = bufferedReader.readLine()) != null ) {
                if (!firstLine) dataStringBuilder.append("\n"); else firstLine = false;
                dataStringBuilder.append(receiveString);
            }

            Logger.logVerbose(LOG_TAG, Logger.getMultiLineLogStringEntry("String", DataUtils.getTruncatedCommandOutput(dataStringBuilder.toString(), Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD, true, false, true), "-"));
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_READING_TEXT_FROM_FILE_FAILED_WITH_EXCEPTION.getError(e, label + "file", filePath, e.getMessage()));
        } finally {
            closeCloseable(fileInputStream);
            closeCloseable(bufferedReader);
        }

        return dataStringBuilder.toString();
    }

    public static class ReadSerializableObjectResult {
        public final Error error;
        public final Serializable serializableObject;

        ReadSerializableObjectResult(Error error, Serializable serializableObject) {
            this.error = error;
            this.serializableObject = serializableObject;
        }
    }

    /**
     * Exception-throwing sibling of {@link #readSerializableObjectFromFile(String, String, Class, boolean)}.
     *
     * @return Returns the deserialized object, or {@code null} if the file did not exist and
     *         {@code ignoreNonExistentFile} was {@code true}.
     * @throws TermuxException If reading was not successful.
     */
    public static <T extends Serializable> T readSerializableObjectFromFileOrThrow(String label, final String filePath, Class<T> readObjectType, final boolean ignoreNonExistentFile) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "readSerializableObjectFromFile"));

        Logger.logVerbose(LOG_TAG, "Reading serializable object from " + label + "file at path \"" + filePath + "\"");

        FileType fileType = getFileType(filePath, false);

        // If file exists but not a regular file
        if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
            throw new TermuxException(FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(label + "file", filePath).setLabel(label + "file"));
        }

        // If file does not exist
        if (fileType == FileType.NO_EXIST) {
            if (ignoreNonExistentFile)
                return null;
            else {
                label += "file meant to be read";
                throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_FOUND_AT_PATH.getError(label, filePath).setLabel(label));
            }
        }

        FileInputStream fileInputStream = null;
        ObjectInputStream objectInputStream = null;
        try {
            fileInputStream = new FileInputStream(filePath);
            objectInputStream = new AllowListingObjectInputStream(fileInputStream, readObjectType);
            T serializableObject = readObjectType.cast(objectInputStream.readObject());
            return serializableObject;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_READING_SERIALIZABLE_OBJECT_TO_FILE_FAILED_WITH_EXCEPTION.getError(e, label + "file", filePath, e.getMessage()));
        } finally {
            closeCloseable(fileInputStream);
            closeCloseable(objectInputStream);
        }
    }

    /**
     * A hardened {@link ObjectInputStream} that only resolves an allow-listed set of classes
     * during {@link #readObject()}, to protect against Java deserialization gadget-chain attacks
     * (CWE-502) if the underlying data is ever attacker-controlled (e.g. via a future path
     * traversal bug, a misconfigured exported component, or a rooted device). See beads-h94.
     *
     * <p>The allow-list is: the specific {@code readObjectType} the caller asked for, its arrays,
     * and a small fixed set of common JDK value types ({@link String} and the primitive wrapper
     * classes) that are needed to deserialize plain data objects but are not themselves usable as
     * gadget-chain entry points (they have no dangerous {@code readObject()}/{@code readResolve()}
     * side effects). Anything else -- including dynamic proxies -- is rejected before the class is
     * even resolved, so a disallowed class's {@code readObject()}/{@code readResolve()} never runs.
     *
     * <p>If a future caller's {@code readObjectType} legitimately needs to deserialize a richer
     * object graph (e.g. containing collections), extend {@link #ALWAYS_ALLOWED_CLASS_NAMES} here
     * deliberately rather than falling back to an unrestricted {@link ObjectInputStream}.
     */
    private static final class AllowListingObjectInputStream extends ObjectInputStream {

        private static final Set<String> ALWAYS_ALLOWED_CLASS_NAMES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            String.class.getName(),
            Boolean.class.getName(), Byte.class.getName(), Short.class.getName(), Integer.class.getName(),
            Long.class.getName(), Float.class.getName(), Double.class.getName(), Character.class.getName()
        )));

        @NonNull
        private final Set<String> allowedClassNames;

        AllowListingObjectInputStream(@NonNull final InputStream in, @NonNull final Class<?> readObjectType) throws IOException {
            super(in);
            Set<String> allowedClassNames = new HashSet<>(ALWAYS_ALLOWED_CLASS_NAMES);
            allowedClassNames.add(readObjectType.getName());
            this.allowedClassNames = allowedClassNames;
        }

        @Override
        protected Class<?> resolveClass(@NonNull final ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            String className = desc.getName();
            if (!isAllowedClassName(className)) {
                throw new InvalidClassException(className,
                    "Deserialization of this class is not allow-listed by AllowListingObjectInputStream.");
            }
            return super.resolveClass(desc);
        }

        @Override
        protected Class<?> resolveProxyClass(@NonNull final String[] interfaces) throws IOException, ClassNotFoundException {
            // Dynamic proxy deserialization is another known gadget-chain vector and is never
            // needed for the plain data objects this method is meant to read.
            throw new InvalidObjectException("Deserialization of dynamic proxy classes is not allowed by AllowListingObjectInputStream.");
        }

        private boolean isAllowedClassName(@NonNull String className) {
            // Class.getName() (which is what ObjectStreamClass.getName() returns) for a plain,
            // non-array class is just its fully-qualified dotted name, e.g. "com.foo.Bar" -- it is
            // NOT wrapped in the "Lcom.foo.Bar;" JVM descriptor form. That wrapped form is only
            // used for the *component type* inside an array class name, e.g. "[Ljava.lang.String;"
            // for String[], or "[I" for int[]. Only strip/interpret "L...;"/array brackets once we
            // have actually confirmed this is an array type name; otherwise compare directly.
            if (!className.startsWith("["))
                return allowedClassNames.contains(className);

            String componentDescriptor = className;
            while (componentDescriptor.startsWith("["))
                componentDescriptor = componentDescriptor.substring(1);

            // A primitive array component type descriptor, e.g. "I" for int[], "J" for long[], etc.
            // Primitives can never carry a readObject()/readResolve() gadget.
            if (!componentDescriptor.startsWith("L"))
                return true;

            // Strip the leading 'L' and trailing ';' from the object array component descriptor.
            String componentClassName = componentDescriptor.endsWith(";")
                ? componentDescriptor.substring(1, componentDescriptor.length() - 1)
                : componentDescriptor.substring(1);

            return allowedClassNames.contains(componentClassName);
        }
    }

    /**
     * Exception-throwing sibling of {@link #writeTextToFile(String, String, Charset, String, boolean)}.
     * @throws TermuxException If writing was not successful.
     */
    public static void writeTextToFileOrThrow(String label, final String filePath, Charset charset, final String dataString, final boolean append) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "writeStringToFile"));

        Logger.logVerbose(LOG_TAG, Logger.getMultiLineLogStringEntry("Writing text to " + label + "file at path \"" + filePath + "\"", DataUtils.getTruncatedCommandOutput(dataString, Logger.LOGGER_ENTRY_MAX_SAFE_PAYLOAD, true, false, true), "-"));

        preWriteToFile(label, filePath);

        if (charset == null) charset = Charset.defaultCharset();

        // Check if charset is supported
        isCharsetSupportedOrThrow(charset);

        FileOutputStream fileOutputStream = null;
        BufferedWriter bufferedWriter = null;
        try {
            // Write text to file
            fileOutputStream = new FileOutputStream(filePath, append);
            bufferedWriter = new BufferedWriter(new OutputStreamWriter(fileOutputStream, charset));

            bufferedWriter.write(dataString);
            bufferedWriter.flush();
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_WRITING_TEXT_TO_FILE_FAILED_WITH_EXCEPTION.getError(e, label + "file", filePath, e.getMessage()));
        } finally {
            closeCloseable(fileOutputStream);
            closeCloseable(bufferedWriter);
        }
    }

    /**
     * Exception-throwing sibling of {@link #writeSerializableObjectToFile(String, String, Serializable)}.
     * @throws TermuxException If writing was not successful.
     */
    public static <T extends Serializable> void writeSerializableObjectToFileOrThrow(String label, final String filePath, final T serializableObject) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "writeSerializableObjectToFile"));

        Logger.logVerbose(LOG_TAG, "Writing serializable object to " + label + "file at path \"" + filePath + "\"");

        preWriteToFile(label, filePath);

        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            // Write serializable object to file
            fileOutputStream = new FileOutputStream(filePath);
            objectOutputStream = new ObjectOutputStream(fileOutputStream);

            objectOutputStream.writeObject(serializableObject);
            objectOutputStream.flush();
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_WRITING_SERIALIZABLE_OBJECT_TO_FILE_FAILED_WITH_EXCEPTION.getError(e, label + "file", filePath, e.getMessage()));
        } finally {
            closeCloseable(fileOutputStream);
            closeCloseable(objectOutputStream);
        }
    }

    private static void preWriteToFile(String label, String filePath) throws TermuxException {
        FileType fileType = getFileType(filePath, false);

        // If file exists but not a regular file
        if (fileType != FileType.NO_EXIST && fileType != FileType.REGULAR) {
            throw new TermuxException(FileUtilsErrno.ERRNO_NON_REGULAR_FILE_FOUND.getError(label + "file", filePath).setLabel(label + "file"));
        }

        // Create the file parent directory
        createParentDirectoryFileOrThrow(label + "file parent", filePath);
    }



    /**
     * Exception-throwing sibling of {@link #isCharsetSupported(Charset)}.
     * @throws TermuxException If charset is not supported or failed to check it.
     */
    public static void isCharsetSupportedOrThrow(final Charset charset) throws TermuxException {
        if (charset == null) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError("charset", "isCharsetSupported"));

        try {
            if (!Charset.isSupported(charset.name())) {
                throw new TermuxException(FileUtilsErrno.ERRNO_UNSUPPORTED_CHARSET.getError(charset.name()));
            }
        } catch (TermuxException e) {
            throw e;
        } catch (Exception e) {
            throw new TermuxException(FileUtilsErrno.ERRNO_CHECKING_IF_CHARSET_SUPPORTED_FAILED.getError(e, charset.name(), e.getMessage()));
        }
    }



    /**
     * Close a {@link Closeable} object if not {@code null} and ignore any exceptions raised.
     *
     * @param closeable The {@link Closeable} object to close.
     */
    public static void closeCloseable(final Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            }
            catch (IOException e) {
                // ignore
            }
        }
    }



    /**
     * Set permissions for file at path. Existing permission outside the {@code permissionsToSet}
     * will be removed.
     *
     * @param filePath The {@code path} for file to set permissions to.
     * @param permissionsToSet The 3 character string that contains the "r", "w", "x" or "-" in-order.
     */
    public static void setFilePermissions(final String filePath, final String permissionsToSet) {
        setFilePermissions(null, filePath, permissionsToSet);
    }

    /**
     * Set permissions for file at path. Existing permission outside the {@code permissionsToSet}
     * will be removed.
     *
     * @param label The optional label for the file. This can optionally be {@code null}.
     * @param filePath The {@code path} for file to set permissions to.
     * @param permissionsToSet The 3 character string that contains the "r", "w", "x" or "-" in-order.
     */
    public static void setFilePermissions(String label, final String filePath, final String permissionsToSet) {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) return;

        if (!isValidPermissionString(permissionsToSet)) {
            Logger.logError(LOG_TAG, "Invalid permissionsToSet passed to setFilePermissions: \"" + permissionsToSet + "\"");
            return;
        }

        File file = new File(filePath);

        if (permissionsToSet.contains("r")) {
            if (!file.canRead()) {
                Logger.logVerbose(LOG_TAG, "Setting read permissions for " + label + "file at path \"" + filePath + "\"");
                file.setReadable(true);
            }
        } else {
            if (file.canRead()) {
                Logger.logVerbose(LOG_TAG, "Removing read permissions for " + label + "file at path \"" + filePath + "\"");
                file.setReadable(false);
            }
        }


        if (permissionsToSet.contains("w")) {
            if (!file.canWrite()) {
                Logger.logVerbose(LOG_TAG, "Setting write permissions for " + label + "file at path \"" + filePath + "\"");
                file.setWritable(true);
            }
        } else {
            if (file.canWrite()) {
                Logger.logVerbose(LOG_TAG, "Removing write permissions for " + label + "file at path \"" + filePath + "\"");
                file.setWritable(false);
            }
        }


        if (permissionsToSet.contains("x")) {
            if (!file.canExecute()) {
                Logger.logVerbose(LOG_TAG, "Setting execute permissions for " + label + "file at path \"" + filePath + "\"");
                file.setExecutable(true);
            }
        } else {
            if (file.canExecute()) {
                Logger.logVerbose(LOG_TAG, "Removing execute permissions for " + label + "file at path \"" + filePath + "\"");
                file.setExecutable(false);
            }
        }
    }



    /**
     * Set missing permissions for file at path. Existing permission outside the {@code permissionsToSet}
     * will not be removed.
     *
     * @param filePath The {@code path} for file to set permissions to.
     * @param permissionsToSet The 3 character string that contains the "r", "w", "x" or "-" in-order.
     */
    public static void setMissingFilePermissions(final String filePath, final String permissionsToSet) {
        setMissingFilePermissions(null, filePath, permissionsToSet);
    }

    /**
     * Set missing permissions for file at path. Existing permission outside the {@code permissionsToSet}
     * will not be removed.
     *
     * @param label The optional label for the file. This can optionally be {@code null}.
     * @param filePath The {@code path} for file to set permissions to.
     * @param permissionsToSet The 3 character string that contains the "r", "w", "x" or "-" in-order.
     */
    public static void setMissingFilePermissions(String label, final String filePath, final String permissionsToSet) {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) return;

        if (!isValidPermissionString(permissionsToSet)) {
            Logger.logError(LOG_TAG, "Invalid permissionsToSet passed to setMissingFilePermissions: \"" + permissionsToSet + "\"");
            return;
        }

        File file = new File(filePath);

        if (permissionsToSet.contains("r") && !file.canRead()) {
            Logger.logVerbose(LOG_TAG, "Setting missing read permissions for " + label + "file at path \"" + filePath + "\"");
            file.setReadable(true);
        }

        if (permissionsToSet.contains("w") && !file.canWrite()) {
            Logger.logVerbose(LOG_TAG, "Setting missing write permissions for " + label + "file at path \"" + filePath + "\"");
            file.setWritable(true);
        }

        if (permissionsToSet.contains("x") && !file.canExecute()) {
            Logger.logVerbose(LOG_TAG, "Setting missing execute permissions for " + label + "file at path \"" + filePath + "\"");
            file.setExecutable(true);
        }
    }



    /**
     * Exception-throwing sibling of {@link #checkMissingFilePermissions(String, String, boolean)}.
     * @throws TermuxException If validating permissions failed.
     */
    public static void checkMissingFilePermissionsOrThrow(final String filePath, final String permissionsToCheck, final boolean ignoreIfNotExecutable) throws TermuxException {
        checkMissingFilePermissionsOrThrow(null, filePath, permissionsToCheck, ignoreIfNotExecutable);
    }

    /**
     * Exception-throwing sibling of {@link #checkMissingFilePermissions(String, String, String, boolean)}.
     * @throws TermuxException If validating permissions failed.
     */
    public static void checkMissingFilePermissionsOrThrow(String label, final String filePath, final String permissionsToCheck, final boolean ignoreIfNotExecutable) throws TermuxException {
        label = (label == null || label.isEmpty() ? "" : label + " ");
        if (filePath == null || filePath.isEmpty()) throw new TermuxException(FunctionErrno.ERRNO_NULL_OR_EMPTY_PARAMETER.getError(label + "file path", "checkMissingFilePermissions"));

        if (!isValidPermissionString(permissionsToCheck)) {
            Logger.logError(LOG_TAG, "Invalid permissionsToCheck passed to checkMissingFilePermissions: \"" + permissionsToCheck + "\"");
            throw new TermuxException(FileUtilsErrno.ERRNO_INVALID_FILE_PERMISSIONS_STRING_TO_CHECK.getError());
        }

        File file = new File(filePath);

        // If file is not readable
        if (permissionsToCheck.contains("r") && !file.canRead()) {
            throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_READABLE.getError(label + "file", filePath).setLabel(label + "file"));
        }

        // If file is not writable
        if (permissionsToCheck.contains("w") && !file.canWrite()) {
            throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_WRITABLE.getError(label + "file", filePath).setLabel(label + "file"));
        }
        // If file is not executable
        // This canExecute() will give "avc: granted { execute }" warnings for target sdk 29
        else if (permissionsToCheck.contains("x") && !file.canExecute() && !ignoreIfNotExecutable) {
            throw new TermuxException(FileUtilsErrno.ERRNO_FILE_NOT_EXECUTABLE.getError(label + "file", filePath).setLabel(label + "file"));
        }
    }



    /**
     * Checks whether string exactly matches the 3 character permission string that
     * contains the "r", "w", "x" or "-" in-order.
     *
     * @param string The {@link String} to check.
     * @return Returns {@code true} if string exactly matches a permission string, otherwise {@code false}.
     */
    public static boolean isValidPermissionString(final String string) {
        if (string == null || string.isEmpty()) return false;
        return Pattern.compile("^([r-])[w-][x-]$", 0).matcher(string).matches();
    }



    /**
     * Get a {@link Error} that contains a shorter version of {@link Errno} message.
     *
     * @param error The original {@link Error} returned by one of the {@link FileUtils} functions.
     * @return Returns the shorter {@link Error} if one exists, otherwise original {@code error}.
     */
    public static Error getShortFileUtilsError(final Error error) {
        String type = error.getType();
        if (!FileUtilsErrno.TYPE.equals(type)) return error;

        Errno shortErrno = FileUtilsErrno.ERRNO_SHORT_MAPPING.get(Errno.valueOf(type, error.getCode()));
        if (shortErrno == null) return error;

        List<Throwable> throwables = error.getThrowablesList();
        if (throwables.isEmpty())
            return shortErrno.getError(DataUtils.getDefaultIfNull(error.getLabel(), "file"));
        else
            return shortErrno.getError(throwables, error.getLabel(), "file");
    }


    /**
     * Get file dirname for file at {@code filePath}.
     *
     * @param filePath The {@code path} for file.
     * @return Returns the file dirname if not {@code null}.
     */
    public static String getFileDirname(String filePath) {
        if (DataUtils.isNullOrEmpty(filePath)) return null;
        int lastSlash = filePath.lastIndexOf('/');
        return (lastSlash == -1) ? null : filePath.substring(0, lastSlash);
    }

    /**
     * Get file basename for file at {@code filePath}.
     *
     * @param filePath The {@code path} for file.
     * @return Returns the file basename if not {@code null}.
     */
    public static String getFileBasename(String filePath) {
        if (DataUtils.isNullOrEmpty(filePath)) return null;
        int lastSlash = filePath.lastIndexOf('/');
        return (lastSlash == -1) ? filePath : filePath.substring(lastSlash + 1);
    }

    /**
     * Get file basename for file at {@code filePath} without extension.
     *
     * @param filePath The {@code path} for file.
     * @return Returns the file basename without extension if not {@code null}.
     */
    public static String getFileBasenameWithoutExtension(String filePath) {
        String fileBasename = getFileBasename(filePath);
        if (DataUtils.isNullOrEmpty(fileBasename)) return null;
        int lastDot = fileBasename.lastIndexOf('.');
        return (lastDot == -1) ? fileBasename : fileBasename.substring(0, lastDot);
    }

}
