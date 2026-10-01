using Microsoft.VisualStudio.TestTools.UnitTesting;
using System;

namespace N7_TestHopDen
{
    [TestClass]
    public class bai13
    {
        public TestContext TestContext { get; set; }

        [TestMethod]
        [DataSource(
    "Microsoft.VisualStudio.TestTools.DataSource.CSV",
    "|DataDirectory|\\Bai13.csv",
    "Bai13#csv",
     DataAccessMethod.Sequential)]
        [DeploymentItem("data_csv\\Bai13.csv")]
        public void TestMax()
        {
            string aValue = TestContext.DataRow[0].ToString();
            string bValue = TestContext.DataRow[1].ToString();
            string cValue = TestContext.DataRow[2].ToString();
            string expected = TestContext.DataRow[3].ToString();

            if (!int.TryParse(aValue, out int A) ||
                !int.TryParse(bValue, out int B) ||
                !int.TryParse(cValue, out int C))
            {
                Assert.AreEqual(
                    "IndexOutOfRangeException",
                    expected);

                return;
            }

            MethodLibrary.MethodLibrary obj = new MethodLibrary.MethodLibrary();

            if (expected == "IndexOutOfRangeException")
            {
                Assert.ThrowsException<IndexOutOfRangeException>(
                    () => obj.Max(A, B, C));

                return;
            }

            int expectedValue =
                Convert.ToInt32(expected);

            int actual = obj.Max(A, B, C);

            Assert.AreEqual(expectedValue, actual);
        }
    }
}
